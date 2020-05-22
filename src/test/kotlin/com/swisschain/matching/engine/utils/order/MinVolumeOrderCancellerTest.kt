package com.swisschain.matching.engine.utils.order

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMultiLimitOrderWrapper
import com.swisschain.matching.engine.utils.assertEquals
import com.swisschain.matching.engine.utils.balance.ReservedVolumesRecalculator
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (MinVolumeOrderCancellerTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MinVolumeOrderCancellerTest : AbstractTest() {

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @TestConfiguration
    class Config {

        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()

            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))

            return testDictionariesDatabaseAccessor
        }
        @Bean
        @Primary
        fun testConfig(): TestSettingsDatabaseAccessor {
            val testSettingsDatabaseAccessor = TestSettingsDatabaseAccessor()
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("100"))
            return testSettingsDatabaseAccessor
        }

    }

    @Autowired
    private lateinit var recalculator: ReservedVolumesRecalculator

    @Before
    fun setUp() {
        testBalanceHolderWrapper.updateBalance(1, "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance(100, "BTC", 2.0)
        testBalanceHolderWrapper.updateBalance(2, "BTC", 3.0)
        testBalanceHolderWrapper.updateBalance(1500, "BTC", 3.0)

        testBalanceHolderWrapper.updateBalance(1, "EUR", 100.0)
        testBalanceHolderWrapper.updateBalance(100, "EUR", 200.0)
        testBalanceHolderWrapper.updateBalance(2, "EUR", 300.0)
        testBalanceHolderWrapper.updateBalance(1500, "EUR", 300.0)

        testBalanceHolderWrapper.updateBalance(1, "USD", 1000.0)
        testBalanceHolderWrapper.updateBalance(100, "USD", 2000.0)
        testBalanceHolderWrapper.updateBalance(2, "USD", 3000.0)
        testBalanceHolderWrapper.updateBalance(1500, "USD", 3000.0)

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "EUR", 5))

        initServices()
    }

    @Test
    fun testCancel() {
        // BTCEUR
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCEUR", price = 9000.0, volume = -1.0)))

        // BTCUSD
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCUSD", price = 10000.0, volume = 0.00001)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "validVolume", walletId = 1, assetId = "BTCUSD", price = 10001.0, volume = 0.01)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 2, assetId = "BTCUSD", price = 10001.0, volume = 0.001)))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper(walletId = 100, pair = "BTCUSD",
                orders = listOf(IncomingLimitOrder(0.00102, 10002.0), IncomingLimitOrder(-0.00001, 11000.0))))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1500, assetId = "BTCUSD", price = 10002.0, volume = -0.001)))


        // EURUSD
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "EURUSD", price = 1.2, volume = -10.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "EURUSD", price = 1.1, volume = 10.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = 2, assetId = "EURUSD", price = 1.3, volume = -4.09)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order2", walletId = 2, assetId = "EURUSD", price = 1.1, volume = 4.09)))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper(walletId = 100, pair = "EURUSD",
                orders = listOf(IncomingLimitOrder(30.0, 1.1), IncomingLimitOrder(-30.0, 1.4))))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1500, assetId = "EURUSD", price = 1.2, volume = 6.0)))


        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5, BigDecimal.valueOf(0.0001)))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2,  BigDecimal.valueOf(5.0)))
        initServices()

        minVolumeOrderCanceller.cancel()

        assertEquals(BigDecimal.valueOf (2.001), testWalletDatabaseAccessor.getBalance(100, "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(100, "BTC"))

        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(100, "USD"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(100, "EUR"))

        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance(1, "BTC"))
        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getReservedBalance(1, "BTC"))

        assertEquals(BigDecimal.valueOf(1007.2), testWalletDatabaseAccessor.getBalance(1, "USD"))
        assertEquals(BigDecimal.valueOf(111.01), testWalletDatabaseAccessor.getReservedBalance(1, "USD"))

        assertEquals(BigDecimal.valueOf(94.0), testWalletDatabaseAccessor.getBalance(1, "EUR"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(1, "EUR"))

        assertEquals(BigDecimal.valueOf(3000.0), testWalletDatabaseAccessor.getBalance(2, "USD"))
        assertEquals(BigDecimal.valueOf(10.01), testWalletDatabaseAccessor.getReservedBalance(2, "USD"))

        assertEquals(BigDecimal.valueOf(300.0), testWalletDatabaseAccessor.getBalance(2, "EUR"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(2, "EUR"))

        // BTCUSD
        assertEquals(1, testOrderDatabaseAccessor.getOrders("BTCUSD", true).filter { it.walletId == 1L }.size)
        // check order is removed from clientOrdersMap
        assertEquals(1, genericLimitOrderService.searchOrders(DEFAULT_BROKER, 1, "BTCUSD", true).size)

        assertEquals("validVolume", testOrderDatabaseAccessor.getOrders("BTCUSD", true).first { it.walletId == 1L }.externalId)

        assertFalse(testOrderDatabaseAccessor.getOrders("BTCUSD", true).any { it.walletId == 100L })
        assertFalse(testOrderDatabaseAccessor.getOrders("BTCUSD", false).any { it.walletId == 100L })

        assertEquals(1, testOrderDatabaseAccessor.getOrders("BTCUSD", true).filter { it.walletId == 2L }.size)
        // check order is removed from clientOrdersMap
        assertEquals(1, genericLimitOrderService.searchOrders(DEFAULT_BROKER, 2, "BTCUSD", true).size)

        // EURUSD
        assertEquals(1, testOrderDatabaseAccessor.getOrders("EURUSD", true).filter { it.walletId == 1L }.size)
        assertFalse(testOrderDatabaseAccessor.getOrders("EURUSD", false).any { it.walletId == 1L })

        assertEquals(1, testOrderDatabaseAccessor.getOrders("EURUSD", true).filter { it.walletId == 100L }.size)
        assertEquals(1, testOrderDatabaseAccessor.getOrders("EURUSD", false).filter { it.walletId == 100L }.size)

        assertFalse(testOrderDatabaseAccessor.getOrders("EURUSD", true).any { it.walletId == 2L })
        assertFalse(testOrderDatabaseAccessor.getOrders("EURUSD", false).any { it.walletId == 2L })

        // check order is removed from ordersMap
        assertNull(genericLimitOrderService.cancelLimitOrder(DEFAULT_BROKER, Date(), "order1", false))
        assertNull(genericLimitOrderService.cancelLimitOrder(DEFAULT_BROKER, Date(), "order2", false))

        assertEquals(1, trustedClientsEventsQueue.size)
        assertEquals(1, (trustedClientsEventsQueue.first() as ExecutionEvent).orders.size)
        assertEquals("11000", (trustedClientsEventsQueue.first() as ExecutionEvent).orders.first().price)

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(5, (clientsEventsQueue.first() as ExecutionEvent).orders.size)

        assertEquals(4, outgoingOrderBookQueue.size)
    }

    @Test
    fun testCancelOrdersWithRemovedAssetPair() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = 1, assetId = "BTCEUR", price = 10000.0, volume = -1.0)))
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCEUR", 100,
                listOf(IncomingLimitOrder(-1.0, price = 10000.0, id = "order2"))))

        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, 1000, 100, "BTC"))
        assertEquals(BigDecimal.valueOf( 1.0), balancesHolder.getReservedBalance(DEFAULT_BROKER, 1000, 1, "BTC"))
        assertEquals(2, testOrderDatabaseAccessor.getOrders("BTCEUR", false).size)
        assertEquals(2, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCEUR").getOrderBook(false).size)

        testDictionariesDatabaseAccessor.clear() // remove asset pair BTCEUR
        initServices()
        minVolumeOrderCanceller.cancel()

        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCEUR", false).size)
        assertEquals(0, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCEUR").getOrderBook(false).size)

        // check order is removed from ordersMap
        assertNull(genericLimitOrderService.cancelLimitOrder(DEFAULT_BROKER, Date(), "order1", false))
        assertNull(genericLimitOrderService.cancelLimitOrder(DEFAULT_BROKER, Date(), "order2", false))

        // check order is removed from clientOrdersMap
        assertEquals(0, genericLimitOrderService.searchOrders(DEFAULT_BROKER, 1, "BTCEUR", false).size)
        assertEquals(0, genericLimitOrderService.searchOrders(DEFAULT_BROKER, 100, "BTCEUR", false).size)

        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getReservedBalance(1, "BTC"))
        assertEquals(BigDecimal.valueOf(1.0), balancesHolder.getReservedBalance(DEFAULT_BROKER, 1000, 1, "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(100, "BTC"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, 1000, 100, "BTC"))

        // recalculate reserved volumes to reset locked reservedAmount
        recalculator.recalculate()
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(1, "BTC"))
    }

}