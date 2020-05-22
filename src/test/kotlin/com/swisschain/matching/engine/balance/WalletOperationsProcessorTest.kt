package com.swisschain.matching.engine.balance

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (WalletOperationsProcessorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WalletOperationsProcessorTest : AbstractTest() {

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("ETH", 6))

            return testDictionariesDatabaseAccessor
        }
    }

    @Test
    fun testPreProcessWalletOperations() {
        testBalanceHolderWrapper.updateBalance(1, "BTC", 1.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "BTC", 0.1)
        initServices()

        val walletOperationsProcessor = balancesHolder.createWalletProcessor(null)

        walletOperationsProcessor.preProcess(
                listOf(
                        WalletOperation(DEFAULT_BROKER, 1000, 1, "BTC", BigDecimal.valueOf(-0.5), BigDecimal.valueOf(-0.1)),
                        WalletOperation(DEFAULT_BROKER, 1000, 2, "ETH", BigDecimal.valueOf(2.0), BigDecimal.valueOf(0.1))
                )
        )

        walletOperationsProcessor.preProcess(
                listOf(WalletOperation(DEFAULT_BROKER, 1000, 2, "ETH", BigDecimal.valueOf(1.0), BigDecimal.valueOf(0.2)))
        )

        assertFailsWith(BalanceException::class) {
            walletOperationsProcessor.preProcess(
                    listOf(
                            WalletOperation(DEFAULT_BROKER, 1000, 1, "BTC", BigDecimal.ZERO, BigDecimal.valueOf(-0.1)),
                            WalletOperation(DEFAULT_BROKER, 1000, 3, "BTC", BigDecimal.valueOf(1.0), BigDecimal.ZERO)
                    )
            )
        }
        assertTrue(walletOperationsProcessor.persistBalances(null, null, null, null))
        walletOperationsProcessor.apply()

        assertBalance(1, "BTC", 0.5, 0.0)
        assertBalance(2, "ETH", 3.0, 0.3)
        assertBalance(3, "BTC", 0.0, 0.0)

//        assertEquals(1,  ((outgoingEventDataQueue.poll() as ExecutionData).executionContext.walletOperationsProcessor.getClientBalanceUpdates().size))
//        val balanceUpdate = (outgoingEventDataQueue.poll() as ExecutionData).executionContext.walletOperationsProcessor.getClientBalanceUpdates()
//        assertEquals(2, balanceUpdate.size)
//
//        val clientBalanceUpdate1 = balanceUpdate.first { it.id == 1 }
//        assertNotNull(clientBalanceUpdate1)
//        assertEquals("BTC", clientBalanceUpdate1.asset)
//        assertEquals(BigDecimal.valueOf(1.0), clientBalanceUpdate1.oldBalance)
//        assertEquals(BigDecimal.valueOf(0.5), clientBalanceUpdate1.newBalance)
//        assertEquals(BigDecimal.valueOf(0.1), clientBalanceUpdate1.oldReserved)
//        assertEquals(BigDecimal.ZERO, clientBalanceUpdate1.newReserved)
//
//        val clientBalanceUpdate2 = balanceUpdate.first { it.id == 2 }
//        assertNotNull(clientBalanceUpdate2)
//        assertEquals("ETH", clientBalanceUpdate2.asset)
//        assertEquals(BigDecimal.ZERO, clientBalanceUpdate2.oldBalance)
//        assertEquals(BigDecimal.valueOf(3.0), clientBalanceUpdate2.newBalance)
//        assertEquals(BigDecimal.ZERO, clientBalanceUpdate2.oldReserved)
//        assertEquals(BigDecimal.valueOf(0.3), clientBalanceUpdate2.newReserved)
    }

    @Test
    fun testForceProcessInvalidWalletOperations() {
        initServices()

        val walletOperationsProcessor = balancesHolder.createWalletProcessor(null)

        walletOperationsProcessor.preProcess(
                listOf(
                        WalletOperation(DEFAULT_BROKER, 1000, 1, "BTC", BigDecimal.ZERO, BigDecimal.valueOf(-0.1))
                ), true)

        assertTrue(walletOperationsProcessor.persistBalances(null, null, null, null))
        walletOperationsProcessor.apply()

        assertBalance(1, "BTC", 0.0, -0.1)
    }

    @Test
    fun testValidation() {
        assertTrue(validate(0, "Asset", 0.0, 0.0, 1.0, 0.0))
        assertTrue(validate(0, "Asset", -1.0, 0.0, -1.0, 0.0))
        assertTrue(validate(0, "Asset", 0.0, -1.0, 0.0, -1.0))
        assertTrue(validate(0, "Asset", 0.0, -1.0, 0.0, -0.9))
        assertTrue(validate(0, "Asset", 0.0, -1.0, 0.2, -1.0))
        assertTrue(validate(0, "Asset", 1.0, 2.0, 1.0, 2.0))
        assertTrue(validate(0, "Asset", 1.0, 2.0, 1.0, 1.9))
        assertTrue(validate(0, "Asset", 0.05, 0.09, 0.0, 0.04))

        assertFalse(validate(0, "Asset", 0.0, 0.0, -1.0, -1.1))
        assertFalse(validate(0, "Asset", 0.0, 0.0, -1.0, 0.0))
        assertFalse(validate(0, "Asset", -1.0, 0.0, -1.1, -0.1))
        assertFalse(validate(0, "Asset", 0.0, 0.0, 0.0, -1.0))
        assertFalse(validate(0, "Asset", 0.0, -1.0, -0.1, -1.0))
        assertFalse(validate(0, "Asset", 0.0, -1.0, 0.0, -1.1))
        assertFalse(validate(0, "Asset", 0.0, 0.0, 1.0, 2.0))
        assertFalse(validate(0, "Asset", 1.0, 2.0, 1.0, 2.1))
        assertFalse(validate(0, "Asset", 1.0, 2.0, -0.1, 0.9))
    }

    @Test
    fun testTrustedClientReservedOperations() {
        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.TRUSTED_CLIENTS, "11", "11", true)
        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.TRUSTED_CLIENTS, "12", "12", true)

        testBalanceHolderWrapper.updateBalance(11, "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance(12, "EUR", 1.0)
        val walletOperationsProcessor = balancesHolder.createWalletProcessor(null)

        walletOperationsProcessor.preProcess(listOf(
                WalletOperation(DEFAULT_BROKER, 1000, 11, "BTC", BigDecimal.ZERO, BigDecimal.valueOf(0.1)),
                WalletOperation(DEFAULT_BROKER, 1000, 12, "ETH", BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1))))

        val clientBalanceUpdates = walletOperationsProcessor.getClientBalanceUpdates()
        assertEquals(1, clientBalanceUpdates.size)
        assertEquals("ETH", clientBalanceUpdates.single().asset)
        assertEquals(12, clientBalanceUpdates.single().walletId)
        assertEquals(BigDecimal.valueOf(0.1), clientBalanceUpdates.single().newBalance)
        assertEquals(BigDecimal.ZERO, clientBalanceUpdates.single().newReserved)

        assertEquals(BigDecimal.ZERO, walletOperationsProcessor.getReservedBalance(DEFAULT_BROKER, 1000, 11, "BTC"))

        walletOperationsProcessor.preProcess(listOf(
                WalletOperation(DEFAULT_BROKER, 1000, 11, "BTC", BigDecimal.ZERO, BigDecimal.valueOf(0.1))),
                allowTrustedClientReservedBalanceOperation = true)

        assertEquals(BigDecimal.valueOf(0.1), walletOperationsProcessor.getReservedBalance(DEFAULT_BROKER, 1000, 11, "BTC"))
    }

    @Test
    fun testNotChangedBalance() {
        val walletOperationsProcessor = balancesHolder.createWalletProcessor(null)

        walletOperationsProcessor.preProcess(listOf(
                WalletOperation(DEFAULT_BROKER, 1000, 1, "BTC", BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1)),
                WalletOperation(DEFAULT_BROKER, 1000, 1, "BTC", BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1)),
                WalletOperation(DEFAULT_BROKER, 1000, 2, "BTC", BigDecimal.valueOf(0.00000001), BigDecimal.ZERO),
                WalletOperation(DEFAULT_BROKER, 1000, 2, "BTC", BigDecimal.valueOf(0.00000001), BigDecimal.valueOf(0.00000001)),
                WalletOperation(DEFAULT_BROKER, 1000, 2, "BTC", BigDecimal.valueOf(-0.00000002), BigDecimal.valueOf(-0.00000001)),
                WalletOperation(DEFAULT_BROKER, 1000, 3, "BTC", BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1))))

        walletOperationsProcessor.preProcess(listOf(
                WalletOperation(DEFAULT_BROKER, 1000, 3, "BTC", BigDecimal.valueOf(-0.1), BigDecimal.valueOf(-0.1))
        ))

        val clientBalanceUpdates = walletOperationsProcessor.getClientBalanceUpdates()
        assertEquals(1, clientBalanceUpdates.size)
        assertEquals("BTC", clientBalanceUpdates.single().asset)
        assertEquals(1, clientBalanceUpdates.single().walletId)
        assertEquals(BigDecimal.ZERO, clientBalanceUpdates.single().oldBalance)
        assertEquals(BigDecimal.ZERO, clientBalanceUpdates.single().oldReserved)
        assertEquals(BigDecimal.valueOf(0.2), clientBalanceUpdates.single().newBalance)
        assertEquals(BigDecimal.valueOf(0.2), clientBalanceUpdates.single().newReserved)
    }

    private fun validate(walletId: Long, assetId: String, oldBalance: Double, oldReserved: Double, newBalance: Double, newReserved: Double): Boolean {
        return try {
            validateBalanceChange(walletId, assetId, BigDecimal.valueOf(oldBalance), BigDecimal.valueOf(oldReserved),
                    BigDecimal.valueOf(newBalance), BigDecimal.valueOf(newReserved))
            true
        } catch (e: BalanceException) {
            false
        }
    }

    private fun assertBalance(walletId: Long, assetId: String, balance: Double, reserved: Double) {
        assertEquals(BigDecimal.valueOf(balance), balancesHolder.getBalance(DEFAULT_BROKER, walletId, assetId))
        assertEquals(BigDecimal.valueOf(reserved), balancesHolder.getReservedBalance(DEFAULT_BROKER, 1000, walletId, assetId))
        assertEquals(BigDecimal.valueOf(balance), testWalletDatabaseAccessor.getBalance(walletId, assetId))
        assertEquals(BigDecimal.valueOf(reserved), testWalletDatabaseAccessor.getReservedBalance(walletId, assetId))
    }
}