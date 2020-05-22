package com.swisschain.matching.engine.utils.balance

import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestReservedVolumesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.database.TestWalletDatabaseAccessor
import com.swisschain.matching.engine.order.utils.TestOrderBookWrapper
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.ReservedBalanceUpdateEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.NumberUtils
import com.swisschain.matching.engine.utils.assertEquals
import com.swisschain.matching.engine.utils.getSetting
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
import java.util.concurrent.BlockingQueue
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (ReservedVolumesRecalculatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReservedVolumesRecalculatorTest {

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
            testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))

            return testDictionariesDatabaseAccessor
        }

        @Bean
        @Primary
        fun testConfig(): TestSettingsDatabaseAccessor {
            val testSettingsDatabaseAccessor = TestSettingsDatabaseAccessor()
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("1001"))
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("1002"))
            return testSettingsDatabaseAccessor
        }
    }

    @Autowired
    protected lateinit var clientsEventsQueue: BlockingQueue<Event>

    @Autowired
    lateinit var testOrderBookWrapper: TestOrderBookWrapper

    @Autowired
    private lateinit var testWalletDatabaseAccessor: TestWalletDatabaseAccessor

    @Autowired
    lateinit var testDictionariesDatabaseAccessor: TestDictionariesDatabaseAccessor

    @Autowired
    lateinit var testBalanceHolderWrapper: TestBalanceHolderWrapper

    @Autowired
    lateinit var reservedVolumesRecalculator: ReservedVolumesRecalculator

    @Autowired
    lateinit var reservedVolumesDatabaseAccessor: TestReservedVolumesDatabaseAccessor

    @Before
    fun setUp() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1001, assetId = "BTCUSD", price = 10000.0, volume = -1.0, reservedVolume = 0.5))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "BTCUSD", price = 10000.0, volume = -1.0, reservedVolume = 0.5))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "1", walletId = 1, assetId = "EURUSD", price = 10000.0, volume = -1.0, reservedVolume = 0.4))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "2", walletId = 1, assetId = "EURUSD", price = 10000.0, volume = -1.0, reservedVolume = 0.3))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "BTCUSD", price = 10000.0, volume = -1.0, reservedVolume = 1.0))

        testOrderBookWrapper.addStopLimitOrder(buildLimitOrder(uid = "3", walletId = 2, assetId = "BTCUSD", type = LimitOrderType.STOP_LIMIT, volume = 0.1, lowerLimitPrice = 9000.0, lowerPrice = 9900.0, reservedVolume = 990.0))
        testOrderBookWrapper.addStopLimitOrder(buildLimitOrder(uid = "4", walletId = 2, assetId = "BTCUSD", type = LimitOrderType.STOP_LIMIT, volume = 0.1, lowerLimitPrice = 10000.0, lowerPrice = 10900.0))

        testBalanceHolderWrapper.updateBalance(1001, "BTC", 10.0)
        testBalanceHolderWrapper.updateReservedBalance(1001, "BTC", 2.0, false)
        // negative reserved balance
        testBalanceHolderWrapper.updateBalance(1002, "BTC", 1.0)
        testBalanceHolderWrapper.updateReservedBalance(1002, "BTC", -0.001, false)

        testBalanceHolderWrapper.updateBalance(3, "BTC", 0.0)
        testBalanceHolderWrapper.updateReservedBalance(3, "BTC", -0.001)


        testBalanceHolderWrapper.updateBalance(1, "USD", 10.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD", 1.0)

        testBalanceHolderWrapper.updateBalance(1, "BTC", 10.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "BTC", 2.0)

        testBalanceHolderWrapper.updateBalance(1, "EUR", 10.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "EUR", 3.0)


        testBalanceHolderWrapper.updateBalance(2, "EUR", 10.0)
        testBalanceHolderWrapper.updateReservedBalance(2, "EUR", 0.0)

        testBalanceHolderWrapper.updateBalance(2, "BTC", 10.0)
        testBalanceHolderWrapper.updateReservedBalance(2, "BTC", 1.0)

        testBalanceHolderWrapper.updateBalance(2, "USD", 990.0)
        testBalanceHolderWrapper.updateReservedBalance(2, "USD", 1.0)
    }

    @Test
    fun testRecalculate() {
        reservedVolumesRecalculator.recalculate()

        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(1001, "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(1002, "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(3, "BTC"))
        assertEquals(BigDecimal.valueOf(0.5), testWalletDatabaseAccessor.getReservedBalance(1, "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(1, "USD"))
        assertEquals(BigDecimal.valueOf(0.7), testWalletDatabaseAccessor.getReservedBalance(1, "EUR"))
        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getReservedBalance(2, "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(2, "EUR"))
        assertEquals(BigDecimal.valueOf(2080.0), testWalletDatabaseAccessor.getReservedBalance(2, "USD"))

        assertEquals(7, reservedVolumesDatabaseAccessor.corrections.size)
        assertEquals("1,2", reservedVolumesDatabaseAccessor.corrections.first { NumberUtils.equalsIgnoreScale(it.newReserved, BigDecimal.valueOf(0.7)) }.orderIds)
        assertEquals("3,4", reservedVolumesDatabaseAccessor.corrections.first { NumberUtils.equalsIgnoreScale(it.newReserved, BigDecimal.valueOf(2080.0)) }.orderIds)

        assertEquals(7, clientsEventsQueue.size)
        assertEvent(1001, "BTC", "10", "2", "0", clientsEventsQueue)
        assertEvent(1002, "BTC", "1", "-0.001", "0", clientsEventsQueue)
        assertEvent(3, "BTC", "0", "-0.001", "0", clientsEventsQueue)
        assertEvent(1, "BTC", "10", "2", "0.5", clientsEventsQueue)
        assertEvent(1, "USD", "10", "1", "0", clientsEventsQueue)
        assertEvent(1, "EUR", "10", "3", "0.7", clientsEventsQueue)
        assertEvent(2, "USD", "990", "1", "2080", clientsEventsQueue)
    }

    private fun assertEvent(walletId: Long, assetId: String, balance: String, oldReserved: String, newReserved: String, events: Collection<Event>) {
        val event = events.single {
            it is ReservedBalanceUpdateEvent && it.reservedBalanceUpdate.walletId == walletId && it.reservedBalanceUpdate.assetId == assetId
        } as ReservedBalanceUpdateEvent
        val message = "Client $walletId, assetId $assetId"
        assertEquals(1, event.balanceUpdates.size)
        val balanceUpdate = event.balanceUpdates.single()
        assertEquals(balance, balanceUpdate.oldBalance, message)
        assertEquals(balance, balanceUpdate.newBalance, message)
        assertEquals(oldReserved, balanceUpdate.oldReserved, message)
        assertEquals(newReserved, balanceUpdate.newReserved, message)
    }

}