package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeSizeType
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.daos.wallet.Wallet
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.grpc.TestStreamObserver
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.outgoing.messages.v2.events.CashInEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.CashOutEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.ReservedBalanceUpdateEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildFeeInstruction
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildFeeInstructions
import com.swisschain.matching.engine.utils.assertEquals
import com.swisschain.matching.engine.utils.getSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (CashInOutOperationServiceTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CashInOutOperationServiceTest : AbstractTest() {

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Autowired
    private lateinit var reservedCashInOutOperationService: ReservedCashInOutOperationService

    @Autowired
    private lateinit var testSettingDatabaseAccessor: TestSettingsDatabaseAccessor

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()

            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("Asset1", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("Asset2", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("Asset3", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("Asset4", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("Asset5", 8))

            return testDictionariesDatabaseAccessor
        }
    }

    @Before
    fun setUp() {
        testBalanceHolderWrapper.updateBalance(1, "Asset1", 100.0)
        testBalanceHolderWrapper.updateBalance(2, "Asset1", 100.0)
        testBalanceHolderWrapper.updateBalance(3, "Asset1", 100.0)
        testBalanceHolderWrapper.updateReservedBalance(3, "Asset1", 50.0)
        initServices()
    }

    @Test
    fun testCashIn() {
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset1", 50.0))
        val balance = testWalletDatabaseAccessor.getBalance(1, "Asset1")
        assertNotNull(balance)
        assertEquals(BigDecimal.valueOf(150.0), balance)

        val cashInEvent = clientsEventsQueue.poll() as CashInEvent
        assertEquals(1, cashInEvent.cashIn.walletId)
        assertEquals("50", cashInEvent.cashIn.volume)
        assertEquals("Asset1", cashInEvent.cashIn.assetId)
        assertEquals("TestDescription", cashInEvent.cashIn.description)

        assertEquals(1, cashInEvent.balanceUpdates.size)
        val balanceUpdate = cashInEvent.balanceUpdates.first()
        assertEquals("100", balanceUpdate.oldBalance)
        assertEquals("150", balanceUpdate.newBalance)
    }

    @Test
    fun testReservedCashIn() {
        val messageWrapper = buildReservedCashInOutWrapper(3, "Asset1", 50.0)
        reservedCashInOutOperationService.processMessage(messageWrapper)
        val balance = testWalletDatabaseAccessor.getBalance(3, "Asset1")
        val reservedBalance = testWalletDatabaseAccessor.getReservedBalance(3, "Asset1")
        assertEquals(BigDecimal.valueOf(100.0), balance)
        assertEquals(BigDecimal.valueOf(100.0), reservedBalance)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ReservedBalanceUpdateEvent

        assertEquals(3, event.reservedBalanceUpdate.walletId)
        assertEquals("Asset1", event.reservedBalanceUpdate.assetId)
        assertEquals("50", event.reservedBalanceUpdate.volume)

        assertEquals(1, event.balanceUpdates.size)
        assertEventBalanceUpdate(3, "Asset1", "100", "100", "50", "100", event.balanceUpdates)
    }

    @Test
    fun testSmallCashIn() {
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset1", 0.01))
        val balance = testWalletDatabaseAccessor.getBalance(1, "Asset1")
        assertNotNull(balance)
        assertEquals(BigDecimal.valueOf(100.01), balance)

        val cashInTransaction = (clientsEventsQueue.poll() as CashInEvent).cashIn
        assertEquals(1, cashInTransaction.walletId)
        assertEquals("0.01", cashInTransaction.volume)
        assertEquals("Asset1", cashInTransaction.assetId)
    }

    @Test
    fun testSmallReservedCashIn() {
        val messageWrapper = buildReservedCashInOutWrapper(3, "Asset1", 0.01)
        reservedCashInOutOperationService.processMessage(messageWrapper)
        val reservedBalance = testWalletDatabaseAccessor.getReservedBalance(3, "Asset1")
        assertEquals(BigDecimal.valueOf(50.01), reservedBalance)

        val operation = (clientsEventsQueue.poll() as ReservedBalanceUpdateEvent).reservedBalanceUpdate
        assertEquals(3, operation.walletId)
        assertEquals("0.01", operation.volume)
        assertEquals("Asset1", operation.assetId)
    }

    @Test
    fun testCashOut() {
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset1", -50.0))
        val balance = testWalletDatabaseAccessor.getBalance(1, "Asset1")
        assertNotNull(balance)
        assertEquals(BigDecimal.valueOf(50.0), balance)

        val cashOutEvent = clientsEventsQueue.poll() as CashOutEvent
        assertEquals(1, cashOutEvent.cashOut.walletId)
        assertEquals("50", cashOutEvent.cashOut.volume)
        assertEquals("Asset1", cashOutEvent.cashOut.assetId)
        assertEquals("TestDescription", cashOutEvent.cashOut.description)

        assertEquals(1, cashOutEvent.balanceUpdates.size)
        val balanceUpdate = cashOutEvent.balanceUpdates.first()
        assertEquals("100", balanceUpdate.oldBalance)
        assertEquals("50", balanceUpdate.newBalance)
    }

    @Test
    fun testReservedCashOut() {
        val messageWrapper = buildReservedCashInOutWrapper(3, "Asset1", -49.0)
        reservedCashInOutOperationService.processMessage(messageWrapper)
        val reservedBalance = testWalletDatabaseAccessor.getReservedBalance(3, "Asset1")
        assertEquals(BigDecimal.valueOf(1.0), reservedBalance)
        val balance = testWalletDatabaseAccessor.getBalance(3, "Asset1")
        assertEquals(BigDecimal.valueOf(100.0), balance)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ReservedBalanceUpdateEvent

        assertEquals(5, event.header.messageType.id)
        assertEquals(3, event.reservedBalanceUpdate.walletId)
        assertEquals("Asset1", event.reservedBalanceUpdate.assetId)
        assertEquals("-49", event.reservedBalanceUpdate.volume)

        assertEquals(1, event.balanceUpdates.size)
        assertEventBalanceUpdate(3, "Asset1", "100", "100", "50", "1", event.balanceUpdates)
    }

    @Test
    fun testReservedCashOutForTrustedClient() {
        testSettingDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("3"))
        applicationSettingsCache.update()

        reservedCashInOutOperationService.processMessage(buildReservedCashInOutWrapper(3, "Asset1", -49.0))
        assertBalance(3, "Asset1", 100.0, 1.0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ReservedBalanceUpdateEvent

        assertEquals(5, event.header.messageType.id)
        assertEquals(3, event.reservedBalanceUpdate.walletId)
        assertEquals("Asset1", event.reservedBalanceUpdate.assetId)
        assertEquals("-49", event.reservedBalanceUpdate.volume)

        assertEquals(1, event.balanceUpdates.size)
        assertEventBalanceUpdate(3, "Asset1", "100", "100", "50", "1", event.balanceUpdates)
    }

    @Test
    fun testCashOutNegative() {
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset1", -50.0))
        val balance = testWalletDatabaseAccessor.getBalance(1, "Asset1")
        assertNotNull(balance)
        assertEquals(BigDecimal.valueOf(50.0), balance)

        val cashOutTransaction = (clientsEventsQueue.poll() as CashOutEvent).cashOut
        assertEquals(1, cashOutTransaction.walletId)
        assertEquals("50", cashOutTransaction.volume)
        assertEquals("Asset1", cashOutTransaction.assetId)

        clearMessageQueues()
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset1", -60.0))
        assertEquals(BigDecimal.valueOf(50.0), balance)
        assertEquals(0, clientsEventsQueue.size)
    }

    @Test
    fun testReservedCashOutNegative() {
        val messageWrapper = buildReservedCashInOutWrapper(3, "Asset1", -24.0)
        reservedCashInOutOperationService.processMessage(messageWrapper)
        var reservedBalance = testWalletDatabaseAccessor.getReservedBalance(3, "Asset1")
        assertEquals(BigDecimal.valueOf(26.0), reservedBalance)

        val operation = (clientsEventsQueue.poll() as ReservedBalanceUpdateEvent).reservedBalanceUpdate
        assertEquals(3, operation.walletId)
        assertEquals("-24", operation.volume)
        assertEquals("Asset1", operation.assetId)

        clearMessageQueues()
        val messageWrapper1 = buildReservedCashInOutWrapper(3, "Asset1", -30.0)
        reservedCashInOutOperationService.processMessage(messageWrapper1)
        reservedBalance = testWalletDatabaseAccessor.getReservedBalance(3, "Asset1")
        assertEquals(BigDecimal.valueOf(26.0), reservedBalance)
        assertEquals(0, clientsEventsQueue.size)
    }

    @Test
    fun testReservedCashInHigherThanBalance() {
        val messageWrapper = buildReservedCashInOutWrapper(3, "Asset1", 50.01)
        reservedCashInOutOperationService.processMessage(messageWrapper)
        val reservedBalance = testWalletDatabaseAccessor.getReservedBalance(3, "Asset1")
        assertEquals(BigDecimal.valueOf(50.0), reservedBalance)
        assertEquals(0, clientsEventsQueue.size)
    }

    @Test
    fun testAddNewAsset() {
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset4", 100.0))
        val balance = testWalletDatabaseAccessor.getBalance(1, "Asset4")

        assertNotNull(balance)
        assertEquals(BigDecimal.valueOf(100.0), balance)
    }

    @Test
    fun testAddNewWallet() {
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(3, "Asset2", 100.0))
        val balance = testWalletDatabaseAccessor.getBalance(3, "Asset2")

        assertNotNull(balance)
        assertEquals(BigDecimal.valueOf(100.0), balance)
    }

    @Test
    fun testRounding() {
        balancesHolder.insertOrUpdateWallets(listOf(Wallet(DEFAULT_BROKER, 1, listOf(AssetBalance(DEFAULT_BROKER, 1,"Asset1", BigDecimal.valueOf(29.99))))), null)
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset1", -0.01))
        val balance = testWalletDatabaseAccessor.getBalance(1, "Asset1")

        assertNotNull(balance)

        assertEquals("29.98", balance.toString())
    }

    @Test
    fun testRoundingWithReserved() {
        testBalanceHolderWrapper.updateBalance(1, "Asset5", 1.00418803)
        testBalanceHolderWrapper.updateReservedBalance(1, "Asset5", 0.00418803)
        initServices()
        
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset5", -1.0))

        assertEquals(1, clientsEventsQueue.size)
        val cashInTransaction = (clientsEventsQueue.poll() as CashOutEvent).cashOut
        assertNotNull(cashInTransaction)
        assertEquals(1, cashInTransaction.walletId)
        assertEquals("1", cashInTransaction.volume)
        assertEquals("Asset5", cashInTransaction.assetId)

    }

    @Test
    fun testCashOutFee() {
        testBalanceHolderWrapper.updateBalance(1, "Asset4", 0.06)
        testBalanceHolderWrapper.updateReservedBalance(1, "Asset4", 0.0)
        testBalanceHolderWrapper.updateBalance(1, "Asset5", 11.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "Asset5", 0.0)
        initServices()
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset5", -1.0,
                fees = buildFeeInstructions(type = FeeType.CLIENT_FEE, size = 0.05, sizeType = FeeSizeType.ABSOLUTE, targetWalletId = 3, assetIds = listOf("Asset4"))))

        assertEquals(BigDecimal.valueOf(0.01), balancesHolder.getBalance(DEFAULT_BROKER, 1, "Asset4"))
        assertEquals(BigDecimal.valueOf(0.05), balancesHolder.getBalance(DEFAULT_BROKER, 3, "Asset4"))
        assertEquals(BigDecimal.valueOf(10.0), balancesHolder.getBalance(DEFAULT_BROKER, 1, "Asset5"))
    }

    @Test
    fun testCashOutInvalidFee() {
        testBalanceHolderWrapper.updateBalance(1, "Asset5", 3.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "Asset5", 0.0)
        initServices()

        // Negative fee size
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset5", -1.0,
                fees = buildFeeInstructions(type = FeeType.CLIENT_FEE, size = -0.1, sizeType = FeeSizeType.PERCENTAGE, targetWalletId = 3)))

        assertEquals(BigDecimal.valueOf(3.0), balancesHolder.getBalance(DEFAULT_BROKER, 1, "Asset5"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getBalance(DEFAULT_BROKER, 3, "Asset5"))

        // Fee amount is more than operation amount
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset5", -0.9,
                fees = buildFeeInstructions(type = FeeType.CLIENT_FEE, size = 0.91, sizeType = FeeSizeType.ABSOLUTE, targetWalletId = 3)))

        // Multiple fee amount is more than operation amount
        cashInOutOperationService.processMessage(messageBuilder.buildCashInOutWrapper(1, "Asset5", -1.0,
                fees = listOf(buildFeeInstruction(type = FeeType.CLIENT_FEE, size = 0.5, sizeType = FeeSizeType.PERCENTAGE, targetWalletId = 3)!!,
                        buildFeeInstruction(type = FeeType.CLIENT_FEE, size = 0.51, sizeType = FeeSizeType.PERCENTAGE, targetWalletId = 3)!!)))

        assertEquals(BigDecimal.valueOf(3.0), balancesHolder.getBalance(DEFAULT_BROKER, 1, "Asset5"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getBalance(DEFAULT_BROKER, 3, "Asset5"))
    }

    private fun buildReservedCashInOutWrapper(walletId: Long, assetId: String, amount: Double, bussinesId: String = UUID.randomUUID().toString()): MessageWrapper {
        return GenericMessageWrapper(MessageType.RESERVED_CASH_IN_OUT_OPERATION.type, IncomingMessages.ReservedCashInOutOperation.newBuilder()
                .setId(bussinesId)
                .setBrokerId(DEFAULT_BROKER)
                .setWalletId(walletId)
                .setAssetId(assetId)
                .setReservedVolume(amount.toString())
                .setTimestamp(Date().createProtobufTimestampBuilder()).build(), TestStreamObserver(), false, messageId = bussinesId)
    }
}