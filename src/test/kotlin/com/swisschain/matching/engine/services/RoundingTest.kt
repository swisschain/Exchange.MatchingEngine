package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus
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
import kotlin.test.assertNotNull

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (RoundingTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoundingTest: AbstractTest() {

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("JPY", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("CHF", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("LKK", 0))
            return testDictionariesDatabaseAccessor
        }
    }

    @Before
    fun setUp() {
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURJPY", "EUR", "JPY", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCCHF", "BTC", "CHF", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "EUR", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCLKK", "BTC", "LKK", 2))
        initServices()
    }

    @Test
    fun testStraightBuy() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.11548, volume = -1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "EUR", 1000.0)
        testBalanceHolderWrapper.updateBalance(4, "USD", 1500.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = 1.0)))

        assertEquals(1, clientsEventsQueue.size)
        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
        assertEquals("1.11548", marketOrderReport.price!!)
        assertEquals(1, marketOrderReport.trades!!.size)

        assertEquals("-1.12", marketOrderReport.trades!!.first().quotingVolume)
        assertEquals("USD", marketOrderReport.trades!!.first().quotingAssetId)
        assertEquals("1", marketOrderReport.trades!!.first().baseVolume)
        assertEquals("EUR", marketOrderReport.trades!!.first().baseAssetId)

        assertEquals(BigDecimal.valueOf(999.0), testWalletDatabaseAccessor.getBalance(3, "EUR"))
        assertEquals(BigDecimal.valueOf(1.12), testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance(4, "EUR"))
        assertEquals(BigDecimal.valueOf(1498.88), testWalletDatabaseAccessor.getBalance(4, "USD"))
    }

    @Test
    fun testStraightSell() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.11548, volume = 1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 1000.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 1500.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1.0)))

        assertEquals(1, clientsEventsQueue.size)
        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
        assertEquals("1.11548", marketOrderReport.price!!)
        assertEquals(1, marketOrderReport.trades!!.size)

        assertEquals("1.11", marketOrderReport.trades!!.first().quotingVolume)
        assertEquals("USD", marketOrderReport.trades!!.first().quotingAssetId)
        assertEquals("-1", marketOrderReport.trades!!.first().baseVolume)
        assertEquals("EUR", marketOrderReport.trades!!.first().baseAssetId)

        assertEquals(BigDecimal.valueOf( 1.0), testWalletDatabaseAccessor.getBalance(3, "EUR"))
        assertEquals(BigDecimal.valueOf(998.89), testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.valueOf(1499.0), testWalletDatabaseAccessor.getBalance(4, "EUR"))
        assertEquals(BigDecimal.valueOf(1.11), testWalletDatabaseAccessor.getBalance(4, "USD"))
    }

//    @Test
//    fun testNotStraightBuy() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.11548, volume = 1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "USD", 1000.0)
//        testBalanceHolderWrapper.updateBalance(4, "EUR", 1500.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = 1.0, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
//        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
//        assertEquals("1.11548", marketOrderReport.price!!)
//        assertEquals(1, marketOrderReport.trades!!.size)
//
//        assertEquals("1", marketOrderReport.trades!!.first().quotingVolume)
//        assertEquals("USD", marketOrderReport.trades!!.first().quotingAssetId)
//        assertEquals("-0.9", marketOrderReport.trades!!.first().baseVolume)
//        assertEquals("EUR", marketOrderReport.trades!!.first().baseAssetId)
//
//        assertEquals(BigDecimal.valueOf(999.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(0.9), testWalletDatabaseAccessor.getBalance(3, "EUR"))
//        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
//        assertEquals(BigDecimal.valueOf(1499.1), testWalletDatabaseAccessor.getBalance(4, "EUR"))
//    }

//    @Test
//    fun testNotStraightSell() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.11548, volume = -1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "EUR", 1000.0)
//        testBalanceHolderWrapper.updateBalance(4, "USD", 1500.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1.0, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
//        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
//        assertEquals("1.11548", marketOrderReport.price!!)
//        assertEquals(1, marketOrderReport.trades!!.size)
//
//        assertEquals("-1", marketOrderReport.trades!!.first().quotingVolume)
//        assertEquals("USD", marketOrderReport.trades!!.first().quotingAssetId)
//        assertEquals("0.89", marketOrderReport.trades!!.first().baseVolume)
//        assertEquals("EUR", marketOrderReport.trades!!.first().baseAssetId)
//
//        assertEquals(BigDecimal.valueOf(999.11), testWalletDatabaseAccessor.getBalance(3, "EUR"))
//        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(0.89), testWalletDatabaseAccessor.getBalance(4, "EUR"))
//        assertEquals(BigDecimal.valueOf(1499.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
//    }

//    @Test
//    fun testNotStraightSellRoundingError() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCCHF", price = 909.727, volume = -1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "BTC", 1.0)
//        testBalanceHolderWrapper.updateBalance(4, "CHF", 1.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCCHF", volume = 	-0.38, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
//        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
//        assertEquals("909.727", marketOrderReport.price!!)
//        assertEquals(1, marketOrderReport.trades!!.size)
//
//        assertEquals("-0.38", marketOrderReport.trades!!.first().quotingVolume)
//        assertEquals("CHF", marketOrderReport.trades!!.first().quotingAssetId)
//        assertEquals("0.0004177", marketOrderReport.trades!!.first().baseVolume)
//        assertEquals("BTC", marketOrderReport.trades!!.first().baseAssetId)
//
//        assertEquals(BigDecimal.valueOf(0.9995823), testWalletDatabaseAccessor.getBalance(3, "BTC"))
//        assertEquals(BigDecimal.valueOf(0.38), testWalletDatabaseAccessor.getBalance(3, "CHF"))
//        assertEquals(BigDecimal.valueOf(0.0004177), testWalletDatabaseAccessor.getBalance(4, "BTC"))
//        assertEquals(BigDecimal.valueOf(0.62), testWalletDatabaseAccessor.getBalance(4, "CHF"))
//    }

    @Test
    fun testStraightBuyBTC() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCUSD", price = 678.229, volume = -1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "BTC", 1000.0)
        testBalanceHolderWrapper.updateBalance(4, "USD", 1500.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCUSD", volume = 1.0)))

        assertEquals(1, clientsEventsQueue.size)
        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
        assertEquals("678.229", marketOrderReport.price)
        assertEquals(1, marketOrderReport.trades!!.size)

        assertEquals("-678.23", marketOrderReport.trades!!.first().quotingVolume)
        assertEquals("USD", marketOrderReport.trades!!.first().quotingAssetId)
        assertEquals("1", marketOrderReport.trades!!.first().baseVolume)
        assertEquals("BTC", marketOrderReport.trades!!.first().baseAssetId)

        assertEquals(BigDecimal.valueOf(999.0), testWalletDatabaseAccessor.getBalance(3, "BTC"))
        assertEquals(BigDecimal.valueOf(678.23), testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance(4, "BTC"))
        assertEquals(BigDecimal.valueOf(821.77), testWalletDatabaseAccessor.getBalance(4, "USD"))
    }

    @Test
    fun testStraightSellBTC() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCUSD", price = 678.229, volume = 1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "USD", 1000.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC", 1500.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCUSD", volume = -1.0)))

        assertEquals(1, clientsEventsQueue.size)
        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
        assertEquals("678.229", marketOrderReport.price!!)
        assertEquals(1, marketOrderReport.trades!!.size)

        assertEquals("678.22", marketOrderReport.trades!!.first().quotingVolume)
        assertEquals("USD", marketOrderReport.trades!!.first().quotingAssetId)
        assertEquals("-1", marketOrderReport.trades!!.first().baseVolume)
        assertEquals("BTC", marketOrderReport.trades!!.first().baseAssetId)

        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance(3, "BTC"))
        assertEquals(BigDecimal.valueOf(321.78), testWalletDatabaseAccessor.getBalance(3, "USD"))
        assertEquals(BigDecimal.valueOf(1499.0), testWalletDatabaseAccessor.getBalance(4, "BTC"))
        assertEquals(BigDecimal.valueOf(678.22), testWalletDatabaseAccessor.getBalance(4, "USD"))
    }

//    @Test
//    fun testNotStraightBuyBTC() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCUSD", price = 678.229, volume = 1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "USD", 1000.0)
//        testBalanceHolderWrapper.updateBalance(4, "BTC", 1500.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCUSD", volume = 1.0, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
//        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
//        assertEquals("678.229", marketOrderReport.price!!)
//        assertEquals(1, marketOrderReport.trades!!.size)
//
//        assertEquals("1", marketOrderReport.trades!!.first().quotingVolume)
//        assertEquals("USD", marketOrderReport.trades!!.first().quotingAssetId)
//
//        assertEquals("-0.00147443", marketOrderReport.trades!!.first().baseVolume)
//        assertEquals("BTC", marketOrderReport.trades!!.first().baseAssetId)
//
//
//        assertEquals(BigDecimal.valueOf(999.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(0.00147443), testWalletDatabaseAccessor.getBalance(3, "BTC"))
//        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
//        assertEquals(BigDecimal.valueOf(1499.99852557), testWalletDatabaseAccessor.getBalance(4, "BTC"))
//    }

//    @Test
//    fun testNotStraightSellBTC() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCUSD", price = 678.229, volume = -1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "BTC", 1000.0)
//        testBalanceHolderWrapper.updateBalance(4, "USD", 1500.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCUSD", volume = -1.0, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
//        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
//        assertEquals("678.229", marketOrderReport.price!!)
//        assertEquals(1, marketOrderReport.trades!!.size)
//
//        assertEquals("-1", marketOrderReport.trades!!.first().quotingVolume)
//        assertEquals("USD", marketOrderReport.trades!!.first().quotingAssetId)
//
//        assertEquals("0.00147442", marketOrderReport.trades!!.first().baseVolume)
//        assertEquals("BTC", marketOrderReport.trades!!.first().baseAssetId)
//
//
//        assertEquals(BigDecimal.valueOf(999.99852558), testWalletDatabaseAccessor.getBalance(3, "BTC"))
//        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance(3, "USD"))
//        assertEquals(BigDecimal.valueOf(0.00147442), testWalletDatabaseAccessor.getBalance(4, "BTC"))
//        assertEquals(BigDecimal.valueOf(1499.0), testWalletDatabaseAccessor.getBalance(4, "USD"))
//    }

//    @Test
//    fun testNotStraightSellBTCMultiLevel() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCLKK", price = 14925.09, volume = -1.34, walletId = 3))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCLKK", price = 14950.18, volume = -1.34, walletId = 3))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCLKK", price = 14975.27, volume = -1.34, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "BTC", 1000.0)
//        testBalanceHolderWrapper.updateBalance(4, "LKK", 50800.0)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCLKK", volume = -50800.0, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
//        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
//        assertEquals("14945.93", marketOrderReport.price!!)
//        assertEquals(3, marketOrderReport.trades!!.size)
//
//        assertEquals(BigDecimal.valueOf(50800.0), testWalletDatabaseAccessor.getBalance(3, "LKK"))
//        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(4, "LKK"))
//    }

//    @Test
//    fun testNotStraightBuyEURJPY() {
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "EURJPY", price = 116.356, volume = 1000.0, walletId = 3))
//        testBalanceHolderWrapper.updateBalance(3, "JPY", 1000.0)
//        testBalanceHolderWrapper.updateBalance(4, "EUR", 0.00999999999999999)
//        initServices()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "EURJPY", volume = 1.16, straight = false)))
//
//        assertEquals(1, clientsEventsQueue.size)
//        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
//        assertEquals(OrderStatus.REJECTED, marketOrderReport.status)
//        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, marketOrderReport.rejectReason)
//    }

    @Test
    fun testStraightSellBTCEUR() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCEUR", price = 597.169, volume = 1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "EUR", 1.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC", 1.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCEUR", volume = -0.0001)))

        assertEquals(1, clientsEventsQueue.size)
        val marketOrderReport = (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.orderType == OrderType.MARKET}
        assertEquals(OrderStatus.MATCHED, marketOrderReport.status)
        assertEquals("597.169", marketOrderReport.price!!)
        assertEquals(1, marketOrderReport.trades!!.size)

        assertEquals("0.05", marketOrderReport.trades!!.first().quotingVolume)
        assertEquals("EUR", marketOrderReport.trades!!.first().quotingAssetId)
        
        assertEquals("-0.0001", marketOrderReport.trades!!.first().baseVolume)
        assertEquals("BTC", marketOrderReport.trades!!.first().baseAssetId)
        

        assertEquals(BigDecimal.valueOf(0.0001), testWalletDatabaseAccessor.getBalance(3, "BTC"))
        assertEquals(BigDecimal.valueOf(0.95), testWalletDatabaseAccessor.getBalance(3, "EUR"))
        assertEquals(BigDecimal.valueOf(0.9999), testWalletDatabaseAccessor.getBalance(4, "BTC"))
        assertEquals(BigDecimal.valueOf(0.05), testWalletDatabaseAccessor.getBalance(4, "EUR"))
    }

    @Test
    fun testLimitOrderRounding() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCEUR", price = 1121.509, volume = 1000.0, walletId = 3))
        testBalanceHolderWrapper.updateBalance(3, "EUR", 1.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC", 1.0)
        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 4, assetId = "BTCEUR", volume = -0.00043722)))

        val limitOrder = testOrderDatabaseAccessor.getOrders("BTCEUR", true).singleOrNull()
        assertNotNull(limitOrder)
        assertEquals(BigDecimal.valueOf(1000.0 - 0.00043722), limitOrder.remainingVolume)
    }
}