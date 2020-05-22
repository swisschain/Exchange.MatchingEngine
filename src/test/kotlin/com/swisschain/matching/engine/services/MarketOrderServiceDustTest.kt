package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderType
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrderWrapper
import com.swisschain.matching.engine.utils.assertEquals
import org.junit.Before
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
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (MarketOrderServiceDustTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MarketOrderServiceDustTest: AbstractTest() {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()

            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("LKK", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("SLR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("GBP", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("CHF", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("JPY", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC1", 8))

            return testDictionariesDatabaseAccessor
        }
    }

    @Before
    fun setUp() {

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURJPY", "EUR", "JPY", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCLKK", "BTC", "LKK", 6))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTC1USD", "BTC1", "USD", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTC1LKK", "BTC1", "LKK", 6))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCCHF", "BTC", "CHF", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("SLRBTC", "SLR", "BTC", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("SLRBTC1", "SLR", "BTC1", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("LKKEUR", "LKK", "EUR", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("LKKGBP", "LKK", "GBP", 5))
        initServices()
    }

    @Test
    fun testDustMatchOneToOne() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCUSD", price = 1000.0, volume = 1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC", 0.020009)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCUSD", volume = -0.02)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        assertEquals(4, eventMarketOrder.walletId)
        assertEquals("1000", eventMarketOrder.price)
        assertEquals(1, eventMarketOrder.trades?.size)
        assertEquals("-0.02", eventMarketOrder.trades!!.first().baseVolume)
        assertEquals("BTC", eventMarketOrder.trades!!.first().baseAssetId)
        assertEquals("20", eventMarketOrder.trades!!.first().quotingVolume)
        assertEquals("USD", eventMarketOrder.trades!!.first().quotingAssetId)
        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)

        assertEquals(BigDecimal.valueOf(0.02), testWalletDatabaseAccessor.getBalance(3, "BTC"))
        assertEquals(BigDecimal.valueOf(1480.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.valueOf(0.000009), testWalletDatabaseAccessor.getBalance(4, "BTC"))
        assertEquals(BigDecimal.valueOf(20.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
    }

//    @Test
//    fun testDustIncorrectBalanceAndDust1() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTC1USD", price = 610.96, volume = 1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)
//        testBalanceHolderWrapper.updateBalance(4, "BTC1", 0.14441494999999982)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = 88.23, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals(4, eventMarketOrder.walletId)
//        assertEquals("610.96", eventMarketOrder.price)
//        assertEquals(1, eventMarketOrder.trades?.size)
//        assertEquals("-0.14441208", eventMarketOrder.trades!!.first().baseVolume)
//        assertEquals("BTC1", eventMarketOrder.trades!!.first().baseAssetId)
//        assertEquals("88.23", eventMarketOrder.trades!!.first().quotingVolume)
//        assertEquals("USD", eventMarketOrder.trades!!.first().quotingAssetId)
//        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)
//
//        assertEquals(BigDecimal.valueOf(0.14441208), testWalletDatabaseAccessor.getBalance(3, "BTC1"))
//        assertEquals(BigDecimal.valueOf(1500 - 88.23), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(0.00000287), testWalletDatabaseAccessor.getBalance(4, "BTC1"))
//        assertEquals(BigDecimal.valueOf(88.23), testWalletDatabaseAccessor.getBalance(4, "USD"))
//    }

//    @Test
//    fun testDustIncorrectBalanceAndDust2() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTC1USD", price = 598.916, volume = 1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)
//        testBalanceHolderWrapper.updateBalance(4, "BTC1", 0.033407)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = 20.0, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals(4, eventMarketOrder.walletId)
//        assertEquals("598.916", eventMarketOrder.price)
//        assertEquals("20", eventMarketOrder.volume)
//        assertFalse(eventMarketOrder.straight!!)
//        assertEquals(1, eventMarketOrder.trades?.size)
//        assertEquals("-0.03339367", eventMarketOrder.trades!!.first().baseVolume)
//        assertEquals("BTC1", eventMarketOrder.trades!!.first().baseAssetId)
//        assertEquals("20", eventMarketOrder.trades!!.first().quotingVolume)
//        assertEquals("USD", eventMarketOrder.trades!!.first().quotingAssetId)
//        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)
//
//        assertEquals(BigDecimal.valueOf(0.03339367), testWalletDatabaseAccessor.getBalance(3, "BTC1"))
//        assertEquals(BigDecimal.valueOf(1500 - 20.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(0.00001333), testWalletDatabaseAccessor.getBalance(4, "BTC1"))
//        assertEquals(BigDecimal.valueOf(20.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
//    }
//
//    @Test
//    fun testDustIncorrectBalanceAndDust3() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTC1USD", price = 593.644, volume = 1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)
//        testBalanceHolderWrapper.updateBalance(4, "BTC1", 0.00092519)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = 0.54, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals(4, eventMarketOrder.walletId)
//        assertEquals("593.644", eventMarketOrder.price)
//        assertEquals("0.54", eventMarketOrder.volume)
//        assertFalse(eventMarketOrder.straight!!)
//        assertEquals(1, eventMarketOrder.trades?.size)
//        assertEquals("-0.00090964", eventMarketOrder.trades!!.first().baseVolume)
//        assertEquals("BTC1", eventMarketOrder.trades!!.first().baseAssetId)
//        assertEquals("0.54", eventMarketOrder.trades!!.first().quotingVolume)
//        assertEquals("USD", eventMarketOrder.trades!!.first().quotingAssetId)
//        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)
//
//        assertEquals(BigDecimal.valueOf(0.00090964), testWalletDatabaseAccessor.getBalance(3, "BTC1"))
//        assertEquals(BigDecimal.valueOf(1500 - 0.54), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(0.00001555), testWalletDatabaseAccessor.getBalance(4, "BTC1"))
//        assertEquals(BigDecimal.valueOf(0.54), testWalletDatabaseAccessor.getBalance(4, "USD"))
//    }

//    @Test
//    fun testDustNotStraight() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1000.0, volume = 500.0, assetId = "BTCUSD", walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "USD", 500.0)
//        testBalanceHolderWrapper.updateBalance(4, "BTC", 0.02001)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCUSD", volume = 20.0, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals(4, eventMarketOrder.walletId)
//        assertEquals("1000", eventMarketOrder.price)
//        assertEquals(1, eventMarketOrder.trades?.size)
//        assertEquals("-0.02", eventMarketOrder.trades!!.first().baseVolume)
//        assertEquals("BTC", eventMarketOrder.trades!!.first().baseAssetId)
//        assertEquals("20", eventMarketOrder.trades!!.first().quotingVolume)
//        assertEquals("USD", eventMarketOrder.trades!!.first().quotingAssetId)
//        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)
//
//        assertEquals(BigDecimal.valueOf(0.02), testWalletDatabaseAccessor.getBalance(3, "BTC"))
//        assertEquals(BigDecimal.valueOf(480.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(20.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
//        assertEquals(BigDecimal.valueOf(0.00001), testWalletDatabaseAccessor.getBalance(4, "BTC"))
//    }

    @Test
    fun testBuyDustStraight() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1000.0, volume = -500.0, assetId = "BTC1USD", walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "BTC1", 0.02001)
        testBalanceHolderWrapper.updateBalance(4, "USD", 500.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = 0.0000272, straight = true)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        assertEquals(4, event.balanceUpdates?.size)
    }

//    @Test
//    fun test_20170309_01() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 0.0000782, volume = -4000.0, assetId = "SLRBTC1", walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "SLR", 238619.65864945)
//        testBalanceHolderWrapper.updateBalance(4, "BTC1", 0.01)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "SLRBTC1", volume = 127.87, straight = true)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals(4, eventMarketOrder.walletId)
//        assertEquals("127.87", eventMarketOrder.volume)
//        assertEquals(1, eventMarketOrder.trades?.size)
//        assertEquals("127.87", eventMarketOrder.trades!!.first().baseVolume)
//        assertEquals("SLR", eventMarketOrder.trades!!.first().baseAssetId)
//        assertEquals("-0.00999944", eventMarketOrder.trades!!.first().quotingVolume)
//        assertEquals("BTC1", eventMarketOrder.trades!!.first().quotingAssetId)
//        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)
//    }
//
//    @Test
//    fun test_20170309_02() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 0.0000782, volume = -4000.0, assetId = "SLRBTC1", walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "SLR", 238619.65864945)
//        testBalanceHolderWrapper.updateBalance(4, "BTC1", 0.01)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "SLRBTC1", volume = -0.01, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//        assertEquals(4, eventMarketOrder.walletId)
//        assertEquals("-0.01", eventMarketOrder.volume)
//        assertEquals(1, eventMarketOrder.trades?.size)
//        assertEquals("127.87", eventMarketOrder.trades!!.first().baseVolume)
//        assertEquals("SLR", eventMarketOrder.trades!!.first().baseAssetId)
//        assertEquals("-0.01", eventMarketOrder.trades!!.first().quotingVolume)
//        assertEquals("BTC1", eventMarketOrder.trades!!.first().quotingAssetId)
//        assertEquals(3, eventMarketOrder.trades!!.first().oppositeWalletId)
//    }

    @Test
    fun testSellDustStraight() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1000.0, volume = 500.0, assetId = "BTC1USD", walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 500.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC1", 0.02001)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = -0.0000272, straight = true)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
    }

//    @Test
//    fun testBuyDustNotStraight() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 19739.43939992, volume = 500.0, assetId = "BTC1LKK", walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "LKK", 500.0)
//        testBalanceHolderWrapper.updateBalance(4, "BTC1", 0.02001)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1LKK", volume = 0.01, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//    }

//    @Test
//    fun testSellDustNotStraight() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 19739.43939992, volume = -500.0, assetId = "BTC1LKK", walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "BTC1", 0.02001)
//        testBalanceHolderWrapper.updateBalance(4, "LKK", 500.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1LKK", volume = -0.01, straight = false)))
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
//        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
//    }

    @Test
    fun testDust1() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1000.0, volume = -0.05, assetId = "BTC1USD", walletId = 3))
        testBalanceHolderWrapper.updateBalance(4, "USD", 5000.0)
        testBalanceHolderWrapper.updateBalance(3, "BTC1", 10.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = 0.04997355, straight = true)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        val eventLimitOrder = event.orders.single { it.orderType == OrderType.LIMIT }
        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, eventLimitOrder.status)

        assertEquals(BigDecimal.valueOf(0.04997355), testWalletDatabaseAccessor.getBalance(4, "BTC1"))
        assertEquals(BigDecimal.valueOf(4950.02), testWalletDatabaseAccessor.getBalance(4, "USD"))
        assertEquals(BigDecimal.valueOf(10 - 0.04997355), testWalletDatabaseAccessor.getBalance(3, "BTC1"))
        assertEquals(BigDecimal.valueOf(49.98), testWalletDatabaseAccessor.getBalance(3, "USD"))
    }

    @Test
    fun testDust2() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1000.0, volume = 0.05, assetId = "BTC1USD", walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 5000.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC1", 10.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = -0.04997355, straight = true)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        val eventLimitOrder = event.orders.single { it.orderType == OrderType.LIMIT }
        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, eventLimitOrder.status)

        assertEquals(BigDecimal.valueOf(0.04997355), testWalletDatabaseAccessor.getBalance(3, "BTC1"))
        assertEquals(BigDecimal.valueOf(4950.03), testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.valueOf(10 - 0.04997355), testWalletDatabaseAccessor.getBalance(4, "BTC1"))
        assertEquals(BigDecimal.valueOf(49.97), testWalletDatabaseAccessor.getBalance(4, "USD"))
    }

    @Test
    fun testDust3() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1000.0, volume = -0.05, assetId = "BTC1USD", walletId = 3))
        testBalanceHolderWrapper.updateBalance(4, "USD", 5000.0)
        testBalanceHolderWrapper.updateBalance(3, "BTC1", 10.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = 0.0499727, straight = true)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        val eventLimitOrder = event.orders.single { it.orderType == OrderType.LIMIT }
        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, eventLimitOrder.status)

        assertEquals(BigDecimal.valueOf(0.0499727), testWalletDatabaseAccessor.getBalance(4, "BTC1"))
        assertEquals(BigDecimal.valueOf(4950.02), testWalletDatabaseAccessor.getBalance(4, "USD"))
        assertEquals(BigDecimal.valueOf(9.9500273), testWalletDatabaseAccessor.getBalance(3, "BTC1"))
        assertEquals(BigDecimal.valueOf(49.98), testWalletDatabaseAccessor.getBalance(3, "USD"))
    }

    @Test
    fun testDust4() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1000.0, volume = 0.05, assetId = "BTC1USD", walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 5000.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC1", 10.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTC1USD", volume = -0.0499727, straight = true)))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        val eventMarketOrder = event.orders.single { it.orderType == OrderType.MARKET }
        assertEquals(OutgoingOrderStatus.MATCHED, eventMarketOrder.status)
        val eventLimitOrder = event.orders.single { it.orderType == OrderType.LIMIT }
        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, eventLimitOrder.status)

        assertEquals(BigDecimal.valueOf(0.0499727), testWalletDatabaseAccessor.getBalance(3, "BTC1"))
        assertEquals(BigDecimal.valueOf(4950.03), testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.valueOf(9.9500273), testWalletDatabaseAccessor.getBalance(4, "BTC1"))
        assertEquals(BigDecimal.valueOf(49.97), testWalletDatabaseAccessor.getBalance(4, "USD"))
    }
}