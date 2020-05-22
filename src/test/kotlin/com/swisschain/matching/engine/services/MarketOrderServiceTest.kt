package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderType
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrderWrapper
import com.swisschain.matching.engine.utils.assertEquals
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (MarketOrderServiceTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MarketOrderServiceTest : AbstractTest() {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()

            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("LKK", 0))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("SLR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("GBP", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("CHF", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 4))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("JPY", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC1", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("ETH", 8))

            return testDictionariesDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Before
    fun setUp() {
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURJPY", "EUR", "JPY", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCCHF", "BTC", "CHF", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCLKK", "BTC", "LKK", 6))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTC1USD", "BTC1", "USD", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCCHF", "BTC", "CHF", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("SLRBTC", "SLR", "BTC", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("LKKEUR", "LKK", "EUR", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("LKKGBP", "LKK", "GBP", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("ETHUSD", "ETH", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "EUR", 8))
        initServices()
    }

//    @Test
//    fun test20062018Accuracy() {
//        testBalanceHolderWrapper.updateBalance(1, "ETH", 1.0)
//        testBalanceHolderWrapper.updateBalance(2, "ETH", 1.0)
//        testBalanceHolderWrapper.updateBalance(3, "USD", 401.9451)
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 523.99, volume = -0.63, walletId = 1, assetId = "ETHUSD"))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 526.531, volume = -0.5, walletId = 2, assetId = "ETHUSD"))
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 3, assetId = "ETHUSD", straight = false, volume = -401.9451)))
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val order = event.orders.single { it.walletId == 3L }
//        assertEquals(OutgoingOrderStatus.MATCHED, order.status)
//        assertEquals(OrderType.MARKET, order.orderType)
//    }

    @Test
    fun testNoLiqudity() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 1))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder()))
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val marketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.REJECTED, marketOrder.status)
        assertEquals(OrderRejectReason.NO_LIQUIDITY, marketOrder.rejectReason)
    }

    @Test
    fun testNotEnoughFundsClientOrder() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.6, volume = 1000.0, walletId = 1))
        testBalanceHolderWrapper.updateBalance(1, "USD", 1000.0)
        testBalanceHolderWrapper.updateBalance(1, "EUR", 1000.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 2))
        testBalanceHolderWrapper.updateBalance(2, "USD", 1500.0)
        testBalanceHolderWrapper.updateBalance(3, "EUR", 1500.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 3, assetId = "EURUSD", volume = -1000.0)))
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.price == "1.6" }.status)
    }

    @Test
    fun testNotEnoughFundsClientMultiOrder() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.6, volume = 1000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 1))
        testBalanceHolderWrapper.updateBalance(1, "USD", 2000.0)
        testBalanceHolderWrapper.updateBalance(3, "EUR", 1500.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 3, assetId = "EURUSD", volume = -1500.0)))
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val marketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.REJECTED, marketOrder.status)
        assertEquals(OrderRejectReason.NO_LIQUIDITY, marketOrder.rejectReason)
    }

    @Test
    fun testNoLiqudityToFullyFill() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 2))
        testBalanceHolderWrapper.updateBalance(2, "USD", 1500.0)
        testBalanceHolderWrapper.updateBalance(3, "EUR", 2000.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 3, assetId = "EURUSD", volume = -2000.0)))
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val marketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.REJECTED, marketOrder.status)
        assertEquals(OrderRejectReason.NO_LIQUIDITY, marketOrder.rejectReason)
    }

    @Test
    fun testNotEnoughFundsMarketOrder() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 900.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0)))
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val marketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.REJECTED, marketOrder.status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, marketOrder.rejectReason)
    }

    @Test
    fun testSmallVolume() {
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5, BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.2)))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(volume = 0.09)))
        var event = clientsEventsQueue.poll() as ExecutionEvent
        var marketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.REJECTED, marketOrder.status)
        assertEquals(OrderRejectReason.TOO_SMALL_VOLUME, marketOrder.rejectReason)
    }

    @Test
    fun testMatchOneToOne() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)
        testBalanceHolderWrapper.updateReservedBalance(3, "USD", 1500.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 1000.0)
        initServices()

        assertEquals(BigDecimal.valueOf(1500.0), testWalletDatabaseAccessor.getReservedBalance(3, "USD"))

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        assertEquals(4, eventMarketOrder.walletId)
        assertEquals("1.5", eventMarketOrder.price)
        assertTrue(eventMarketOrder.straight!!)
        assertEquals(1, eventMarketOrder.trades?.size)
        assertEquals("-1000", eventMarketOrder.trades!!.first().baseVolume)
        assertEquals("EUR", eventMarketOrder.trades!!.first().baseAssetId)
        assertEquals("1500", eventMarketOrder.trades!!.first().quotingVolume)
        assertEquals("USD", eventMarketOrder.trades!!.first().quotingAssetId)
        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)

        assertEquals(BigDecimal.valueOf(1000.0), testWalletDatabaseAccessor.getBalance(3, "EUR"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(4, "EUR"))
        assertEquals(BigDecimal.valueOf(1500.0), testWalletDatabaseAccessor.getBalance(4, "USD"))

        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(3, "USD"))
    }

//    @Test
//    fun testMatchOneToOneEURJPY() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "EURJPY", price = 122.512, volume = 1000000.0, walletId = 3))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "EURJPY", price = 122.524, volume = -1000000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "JPY", 5000000.0)
//        testBalanceHolderWrapper.updateBalance(3, "EUR", 5000000.0)
//        testBalanceHolderWrapper.updateBalance(4, "EUR", 0.1)
//        testBalanceHolderWrapper.updateBalance(4, "JPY", 100.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURJPY", volume = 10.0, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals(4, eventMarketOrder.walletId)
//        assertEquals("122.512", eventMarketOrder.price)
//        assertFalse(eventMarketOrder.straight!!)
//        assertEquals(1, eventMarketOrder.trades?.size)
//        assertEquals("-0.09", eventMarketOrder.trades!!.first().baseVolume)
//        assertEquals("EUR", eventMarketOrder.trades!!.first().baseAssetId)
//        assertEquals("10", eventMarketOrder.trades!!.first().quotingVolume)
//        assertEquals("JPY", eventMarketOrder.trades!!.first().quotingAssetId)
//        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)
//
//        assertEquals(BigDecimal.valueOf(5000000.09), testWalletDatabaseAccessor.getBalance(3, "EUR"))
//        assertEquals(BigDecimal.valueOf(4999990.0), testWalletDatabaseAccessor.getBalance(3, "JPY"))
//        assertEquals(BigDecimal.valueOf(0.01), testWalletDatabaseAccessor.getBalance(4, "EUR"))
//        assertEquals(BigDecimal.valueOf(110.0), testWalletDatabaseAccessor.getBalance(4, "JPY"))
//    }

    @Test
    fun testMatchOneToOneAfterNotEnoughFunds() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0)))
        assertEquals(1, clientsEventsQueue.size)
        var event = clientsEventsQueue.poll() as ExecutionEvent
        var eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.REJECTED, eventMarketOrder.status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, eventMarketOrder.rejectReason)
        assertEquals(0, eventMarketOrder.trades?.size)

        balancesHolder.updateBalance(
                ProcessedMessage(MessageType.CASH_IN_OUT_OPERATION.type, System.currentTimeMillis(), "test"), 0, DEFAULT_BROKER, 4, "EUR", BigDecimal.valueOf(1000.0))
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0)))
        assertEquals(1, clientsEventsQueue.size)
        event = clientsEventsQueue.poll() as ExecutionEvent
        eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        assertEquals("1.5", eventMarketOrder.price)
        assertEquals(1, eventMarketOrder.trades?.size)

        assertEquals(BigDecimal.valueOf(1000.0), testWalletDatabaseAccessor.getBalance(3, "EUR"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(4, "EUR"))
        assertEquals(BigDecimal.valueOf(1500.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
    }

    @Test
    fun testMatchOneToMany() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = 100.0, walletId = 3))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.4, volume = 1000.0, walletId = 1))
        testBalanceHolderWrapper.updateBalance(1, "USD", 1560.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD", 1400.0)
        testBalanceHolderWrapper.updateBalance(3, "USD", 150.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 1000.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        assertEquals("1.41", eventMarketOrder.price)
        assertEquals(2, eventMarketOrder.trades?.size)

        assertEquals(BigDecimal.valueOf(100.0), testWalletDatabaseAccessor.getBalance(3, "EUR"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.valueOf(900.0), testWalletDatabaseAccessor.getBalance(1, "EUR"))
        assertEquals(BigDecimal.valueOf(300.0), testWalletDatabaseAccessor.getBalance(1, "USD"))
        assertEquals(BigDecimal.valueOf(140.0), testWalletDatabaseAccessor.getReservedBalance(1, "USD"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(4, "EUR"))
        assertEquals(BigDecimal.valueOf(1410.0), testWalletDatabaseAccessor.getBalance(4, "USD"))

        val dbBids = testOrderDatabaseAccessor.getOrders("EURUSD", true)
        assertEquals(1, dbBids.size)
        assertEquals(OrderStatus.Processing.name, dbBids.first().status)
    }

    @Test
    fun testMatchOneToMany2016Nov10() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "LKKEUR", price = 0.04412, volume = -20000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "LKKEUR", price = 0.04421, volume = -20000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "LKKEUR", price = 0.04431, volume = -20000.0, walletId = 1))
        testBalanceHolderWrapper.updateBalance(1, "LKK", 6569074.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 7500.02)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "LKKEUR", volume = 50000.0)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        assertEquals("0.0442", eventMarketOrder.price)
        assertEquals(3, eventMarketOrder.trades?.size)

        assertEquals(BigDecimal.valueOf(2209.7), testWalletDatabaseAccessor.getBalance(1, "EUR"))
        assertEquals(BigDecimal.valueOf(6519074.0), testWalletDatabaseAccessor.getBalance(1, "LKK"))
        assertEquals(BigDecimal.valueOf(5290.32), testWalletDatabaseAccessor.getBalance(4, "EUR"))
        assertEquals(BigDecimal.valueOf(50000.0), testWalletDatabaseAccessor.getBalance(4, "LKK"))
    }

//    @Test
//    fun testMatchOneToMany2016Nov10_2() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCLKK", price = 13611.625476, volume = 1.463935, walletId = 1))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCLKK", price = 13586.531910, volume = 1.463935, walletId = 1))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCLKK", price = 13561.438344, volume = 1.463935, walletId = 1))
//        testBalanceHolderWrapper.updateBalance(1, "LKK", 100000.0)
//        testBalanceHolderWrapper.updateBalance(4, "BTC", 12.67565686)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCLKK", volume = 50000.0, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals("13591.031869", eventMarketOrder.price)
//        assertEquals(3, eventMarketOrder.trades?.size)
//
//        assertEquals(BigDecimal.valueOf(3.67889654), testWalletDatabaseAccessor.getBalance(1, "BTC"))
//        assertEquals(BigDecimal.valueOf(50000.0), testWalletDatabaseAccessor.getBalance(1, "LKK"))
//        assertEquals(BigDecimal.valueOf(8.99676032), testWalletDatabaseAccessor.getBalance(4, "BTC"))
//        assertEquals(BigDecimal.valueOf(50000.0), testWalletDatabaseAccessor.getBalance(4, "LKK"))
//    }

//    @Test
//    fun testMatchOneToMany2016Nov10_3() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "LKKGBP", price = 0.0385, volume = -20000.0, walletId = 1))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "LKKGBP", price = 0.03859, volume = -20000.0, walletId = 1))
//        testBalanceHolderWrapper.updateBalance(1, "LKK", 100000.0)
//        testBalanceHolderWrapper.updateBalance(4, "GBP", 982.78)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "LKKGBP", volume = -982.78, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals("0.03851", eventMarketOrder.price)
//        assertEquals(2, eventMarketOrder.trades?.size)
//
//        assertEquals(BigDecimal.valueOf(982.78), testWalletDatabaseAccessor.getBalance(1, "GBP"))
//        assertEquals(BigDecimal.valueOf(74487.0), testWalletDatabaseAccessor.getBalance(1, "LKK"))
//        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(4, "GBP"))
//        assertEquals(BigDecimal.valueOf(25513.0), testWalletDatabaseAccessor.getBalance(4, "LKK"))
//    }

    @Test
    fun testMatchOneToMany2016Dec12() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "SLRBTC", price = 0.00008826, volume = -4000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "SLRBTC", price = 0.00008844, volume = -4000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "SLRBTC", price = 0.00008861, volume = -4000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "SLRBTC", price = 0.00008879, volume = -4000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "SLRBTC", price = 0.00008897, volume = -4000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "SLRBTC", price = 0.00008914, volume = -4000.0, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "SLRBTC", price = 0.00008932, volume = -4000.0, walletId = 1))
        testBalanceHolderWrapper.updateBalance(1, "SLR", 100000.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC", 31.95294)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "SLRBTC", volume = 25000.0, straight = true)))

        assertEquals(BigDecimal.valueOf(2.21816), testWalletDatabaseAccessor.getBalance(1, "BTC"))
        assertEquals(BigDecimal.valueOf(75000.0), testWalletDatabaseAccessor.getBalance(1, "SLR"))
        assertEquals(BigDecimal.valueOf(29.73478), testWalletDatabaseAccessor.getBalance(4, "BTC"))
        assertEquals(BigDecimal.valueOf(25000.0), testWalletDatabaseAccessor.getBalance(4, "SLR"))
    }

    @Test
    fun testMatchOneToMany2016Dec12_2() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 791.37, volume = 4000.0, walletId = 1))
        testBalanceHolderWrapper.updateBalance(1, "CHF", 100000.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC", 0.00036983)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCCHF", volume = -0.00036983, straight = true)))

        assertEquals(BigDecimal.valueOf(0.00036983), testWalletDatabaseAccessor.getBalance(1, "BTC"))
        assertEquals(BigDecimal.valueOf(99999.71), testWalletDatabaseAccessor.getBalance(1, "CHF"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(4, "BTC"))
        assertEquals(BigDecimal.valueOf(0.29), testWalletDatabaseAccessor.getBalance(4, "CHF"))
    }

//    @Test
//    fun testNotStraight() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = -500.0, assetId = "EURUSD", walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "EUR", 500.0)
//        testBalanceHolderWrapper.updateBalance(4, "USD", 750.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -750.0, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals("1.5", eventMarketOrder.price)
//        assertEquals(1, eventMarketOrder.trades?.size)
//
//        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(3, "EUR"))
//        assertEquals(BigDecimal.valueOf(750.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(500.0), testWalletDatabaseAccessor.getBalance(4, "EUR"))
//        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(4, "USD"))
//    }

//    @Test
//    fun testNotStraightMatchOneToMany() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.4, volume = -100.0, walletId = 3))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.5, volume = -1000.0, walletId = 1))
//        testBalanceHolderWrapper.updateBalance(1, "EUR", 3000.0)
//        testBalanceHolderWrapper.updateBalance(3, "EUR", 3000.0)
//        testBalanceHolderWrapper.updateBalance(4, "USD", 2000.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1490.0, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        assertEquals(3, event.orders.size)
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals("1.49", eventMarketOrder.price)
//        assertEquals(2, eventMarketOrder.trades?.size)
//
//        assertEquals(BigDecimal.valueOf(2900.0), testWalletDatabaseAccessor.getBalance(3, "EUR"))
//        assertEquals(BigDecimal.valueOf(140.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(2100.0), testWalletDatabaseAccessor.getBalance(1, "EUR"))
//        assertEquals(BigDecimal.valueOf(1350.0), testWalletDatabaseAccessor.getBalance(1, "USD"))
//        assertEquals(BigDecimal.valueOf(1000.0), testWalletDatabaseAccessor.getBalance(4, "EUR"))
//        assertEquals(BigDecimal.valueOf(510.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
//    }

    @Test
    fun testMatch1() {
        testBalanceHolderWrapper.updateBalance(1, "BTC", 100028.39125545)
        testBalanceHolderWrapper.updateBalance(3, "CHF", 182207.39)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4071.121, volume = -0.00662454, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4077.641, volume = -0.01166889, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4084.382, volume = -0.01980138, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4091.837, volume = -0.02316231, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4098.155, volume = -0.03013115, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4105.411, volume = -0.03790487, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4114.279, volume = -0.03841106, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4120.003, volume = -0.04839733, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4127.137, volume = -0.04879837, walletId = 1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 4136.9, volume = -0.06450525, walletId = 1))
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 3, assetId = "BTCCHF", volume = 0.3)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        assertEquals("4111.117", eventMarketOrder.price)
        assertEquals(10, eventMarketOrder.trades?.size)

        assertEquals(BigDecimal.valueOf(4136.9), genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCCHF").getAskPrice())
    }

    @Test
    fun testMatchWithNotEnoughFundsOrder1() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD", 1.19)
        testBalanceHolderWrapper.updateBalance(2, "EUR", 1000.0)
        testBalanceHolderWrapper.updateBalance(3, "USD", 1000.0)

        val order = buildLimitOrder(assetId = "EURUSD", price = 1.2, volume = 1.0, walletId = 1)
        order.reservedLimitVolume = BigDecimal.valueOf(1.19)
        testOrderBookWrapper.addLimitOrder(order)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, assetId = "EURUSD", price = 1.19, volume = 2.1))

        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(assetId = "EURUSD", volume = -2.0, walletId = 2)))

        assertEquals(1, testOrderDatabaseAccessor.getOrders("EURUSD", true).size)

        assertEquals(BigDecimal.valueOf(1000.0), testWalletDatabaseAccessor.getBalance(1, "USD"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(1, "USD"))
        assertEquals(0, trustedClientsEventsQueue.size)
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventCancelledOrders = event.orders.filter { it.status == OutgoingOrderStatus.CANCELLED }
        assertEquals(1, eventCancelledOrders.size)
        assertEquals(1, eventCancelledOrders.single().walletId)

        val eventBalanceUpdate = event.balanceUpdates!!.single { it.walletId == 1L }
        assertEquals("0", eventBalanceUpdate.newReserved)
    }

    @Test
    fun testMatchWithNotEnoughFundsOrder2() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD", 1.19)
        testBalanceHolderWrapper.updateBalance(2, "EUR", 1000.0)
        testBalanceHolderWrapper.updateBalance(3, "USD", 1000.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "EURUSD", price = 1.2, volume = 1.0, reservedVolume = 1.19))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, assetId = "EURUSD", price = 1.19, volume = 2.1))

        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(assetId = "EURUSD", volume = -2.0, walletId = 2)))

        assertBalance(1, "USD", 1000.0, 0.0)
    }

    @Test
    fun testMatchWithNotEnoughFundsOrder3() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 1.19)
        testBalanceHolderWrapper.updateBalance(2, "EUR", 1000.0)
        testBalanceHolderWrapper.updateBalance(3, "USD", 1000.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "EURUSD", price = 1.2, volume = 1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, assetId = "EURUSD", price = 1.19, volume = 2.1))

        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(assetId = "EURUSD", volume = -2.0, walletId = 2)))

        val balanceUpdates = (clientsEventsQueue.poll() as ExecutionEvent).balanceUpdates
        assertEquals(0, balanceUpdates!!.filter { it.walletId == 1L }.size)
    }

    @Test
    fun testMatchSellMinRemaining() {
        testBalanceHolderWrapper.updateBalance(1, "EUR", 50.00)
        testBalanceHolderWrapper.updateBalance(2, "BTC", 0.01000199)
        testBalanceHolderWrapper.updateBalance(3, "EUR", 49.99)

        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(assetId = "BTCEUR", price = 5000.0, volume = 0.01, walletId = 1)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(assetId = "BTCEUR", price = 4999.0, volume = 0.01, walletId = 3)))

        clearMessageQueues()
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 2, assetId = "BTCEUR", volume = -0.01000199)))

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.size)
        val order = event.orders.single { it.walletId == 2L }
        assertEquals(OutgoingOrderStatus.REJECTED, order.status)
        assertEquals(OrderRejectReason.INVALID_VOLUME_ACCURACY, order.rejectReason)
        assertEquals(0, order.trades?.size)

        assertOrderBookSize("BTCEUR", true, 2)
    }

    @Test
    fun testStraightOrderMaxValue() {
        testBalanceHolderWrapper.updateBalance(1, "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance(2, "USD", 10001.0)
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8,
                maxValue = BigDecimal.valueOf(10000)))
        assetPairsCache.update()

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "BTCUSD", volume = 1.0, price = 10001.0))

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 1, assetId = "BTCUSD", volume = -1.0)))

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.size)
        val eventOrder = event.orders.single()
        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
        assertEquals(OrderRejectReason.INVALID_VALUE, eventOrder.rejectReason)

        assertOrderBookSize("BTCUSD", true, 1)
    }

//    @Test
//    fun testNotStraightOrderMaxValue() {
//        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8,
//                maxValue = BigDecimal.valueOf(10000)))
//        assetPairsCache.update()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 1, assetId = "BTCUSD", volume = 10001.0, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        assertEquals(1, event.orders.size)
//        val eventOrder = event.orders.single()
//        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
//        assertEquals(OrderRejectReason.INVALID_VALUE, eventOrder.rejectReason)
//    }

    @Test
    fun testStraightOrderMaxVolume() {
        testBalanceHolderWrapper.updateBalance(1, "BTC", 1.1)
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8,
                maxVolume = BigDecimal.valueOf(1.0)))
        assetPairsCache.update()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 1, assetId = "BTCUSD", volume = -1.1)))

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.size)
        val eventOrder = event.orders.single()
        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
        assertEquals(OrderRejectReason.INVALID_VOLUME, eventOrder.rejectReason)
    }

    @Test
    fun testNotStraightOrderMaxVolume() {
        testBalanceHolderWrapper.updateBalance(1, "BTC", 1.1)
        testBalanceHolderWrapper.updateBalance(2, "USD", 11000.0)
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8,
                maxVolume = BigDecimal.valueOf(1.0)))
        assetPairsCache.update()

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "BTCUSD", volume = 1.1, price = 10000.0))

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 1, assetId = "BTCUSD", volume = 11000.0, straight = false)))

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.size)
        val eventOrder = event.orders.single()
        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
        assertEquals(OrderRejectReason.INVALID_VOLUME, eventOrder.rejectReason)

        assertOrderBookSize("BTCUSD", true, 1)
    }

    @Test
    fun testBuyPriceDeviationThreshold() {
        testBalanceHolderWrapper.updateBalance(1, "EUR", 2.0)
        testBalanceHolderWrapper.updateBalance(2, "USD", 3.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "EURUSD", price = 1.1, volume = -1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "EURUSD", price = 1.2, volume = -1.0))

        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.MO_PRICE_DEVIATION_THRESHOLD, "EURUSD", "0.0", true)

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 2, assetId = "EURUSD", volume = 2.0)))
        var eventOrder = (clientsEventsQueue.single() as ExecutionEvent).orders.single()
        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
        assertEquals(OrderRejectReason.TOO_HIGH_PRICE_DEVIATION, eventOrder.rejectReason)

        // default threshold from app settings
        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.MO_PRICE_DEVIATION_THRESHOLD, "EURUSD", "0.04", true)

        clearMessageQueues()
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 2, assetId = "EURUSD", volume = 2.0)))
        eventOrder = (clientsEventsQueue.single() as ExecutionEvent).orders.single()
        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
        assertEquals(OrderRejectReason.TOO_HIGH_PRICE_DEVIATION, eventOrder.rejectReason)

        // threshold from asset pairs dictionary
        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.MO_PRICE_DEVIATION_THRESHOLD, "EURUSD", "0.05", true)
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5,
                marketOrderPriceDeviationThreshold = BigDecimal.valueOf(0.04)))
        assetPairsCache.update()

        clearMessageQueues()
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 2, assetId = "EURUSD", volume = 2.0)))
        eventOrder = (clientsEventsQueue.single() as ExecutionEvent).orders.single()
        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
        assertEquals(OrderRejectReason.TOO_HIGH_PRICE_DEVIATION, eventOrder.rejectReason)

        // default threshold from app settings to match order
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        assetPairsCache.update()

        clearMessageQueues()
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 2, assetId = "EURUSD", volume = 2.0)))
        eventOrder = (clientsEventsQueue.single() as ExecutionEvent).orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventOrder.status)
    }

    @Test
    fun testSellPriceDeviationThreshold() {
        testBalanceHolderWrapper.updateBalance(1, "EUR", 2.0)
        testBalanceHolderWrapper.updateBalance(2, "USD", 3.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "EURUSD", price = 1.0, volume = 1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "EURUSD", price = 0.9, volume = 1.0))

        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.MO_PRICE_DEVIATION_THRESHOLD, "EURUSD", "0.0", true)

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 1, assetId = "EURUSD", volume = -2.0)))
        var eventOrder = (clientsEventsQueue.single() as ExecutionEvent).orders.single()
        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
        assertEquals(OrderRejectReason.TOO_HIGH_PRICE_DEVIATION, eventOrder.rejectReason)

        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.MO_PRICE_DEVIATION_THRESHOLD, "EURUSD", "0.04", true)

        clearMessageQueues()
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 1, assetId = "EURUSD", volume = -2.0)))
        eventOrder = (clientsEventsQueue.single() as ExecutionEvent).orders.single()
        assertEquals(OutgoingOrderStatus.REJECTED, eventOrder.status)
        assertEquals(OrderRejectReason.TOO_HIGH_PRICE_DEVIATION, eventOrder.rejectReason)

        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.MO_PRICE_DEVIATION_THRESHOLD, "EURUSD", "0.05", true)

        clearMessageQueues()
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 1, assetId = "EURUSD", volume = -2.0)))
        eventOrder = (clientsEventsQueue.single() as ExecutionEvent).orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventOrder.status)
    }

//    @Test
//    fun testCancelLimitOrdersAfterRejectedMarketOrder() {
//        testBalanceHolderWrapper.updateBalance(1, "BTC", 0.1)
//        testBalanceHolderWrapper.updateBalance(2, "USD", 1000.0)
//        testBalanceHolderWrapper.updateBalance(3, "BTC", 0.1)
//        testBalanceHolderWrapper.updateReservedBalance(3, "BTC", 0.1)
//
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, assetId = "BTCUSD", volume = -0.1, price = 5000.0,
//                // 'not enough funds' fee to cancel this order during matching
//                fees = listOf(NewLimitOrderFeeInstruction(FeeType.CLIENT_FEE, null, null, FeeSizeType.PERCENTAGE, BigDecimal.valueOf(0.1), null, 500, listOf("EUR"), null))
//        ))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = -0.1, price = 6000.0))
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 2, assetId = "BTCUSD", volume = -1000.0, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//
//        assertEquals(1, event.balanceUpdates?.size)
//        assertEquals(3, event.balanceUpdates!!.single().walletId)
//        assertEquals("BTC", event.balanceUpdates!!.single().assetId)
//        assertEquals("0.1", event.balanceUpdates!!.single().oldReserved)
//        assertEquals("0", event.balanceUpdates!!.single().newReserved)
//
//        assertEquals(2, event.orders.size)
//
//        val marketOrder = event.orders.single { it.walletId == 2L }
//        assertEquals(OutgoingOrderStatus.REJECTED, marketOrder.status)
//        assertEquals(OrderRejectReason.NO_LIQUIDITY, marketOrder.rejectReason)
//        assertEquals(0, marketOrder.trades?.size)
//
//        val cancelledLimitOrder = event.orders.single {it.walletId == 3L}
//        assertEquals(OutgoingOrderStatus.CANCELLED, cancelledLimitOrder.status)
//        assertEquals(0, cancelledLimitOrder.trades?.size)
//
//        assertOrderBookSize("BTCUSD", false, 1)
//        assertEquals(BigDecimal.valueOf(6000.0), genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getAskPrice())
//
//        assertBalance(3, "BTC", 0.1, 0.0)
//    }
}
