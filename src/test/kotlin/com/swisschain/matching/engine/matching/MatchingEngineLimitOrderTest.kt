package com.swisschain.matching.engine.matching

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrderFeeInstructions
import com.swisschain.matching.engine.utils.assertEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import java.math.BigDecimal
import kotlin.test.assertNotNull

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MatchingEngineLimitOrderTest : MatchingEngineTest() {

    @Test
    fun testMatchLimitOrderBuyWithEmptyOrderBook() {
        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchLimitOrderSellWithEmptyOrderBook() {
        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, remainingVolume = BigDecimal.valueOf( -100.0), status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchLimitOrderWithSameOrderBookSide() {
        testBalanceHolderWrapper.updateBalance(2, "USD", 1000.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = 100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, orderBookSize = 1, status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchNoLiquidityLimitOrderBuy() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.3, volume = -100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, orderBookSize = 1, status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchNoLiquidityLimitOrderSell() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.2, volume = 100.0))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.3, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, remainingVolume = BigDecimal.valueOf(-100.0), orderBookSize = 1, status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchLimitOrderWithAnotherAssetPair() {
        testBalanceHolderWrapper.updateBalance(2, "BTC", 100.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "BTCUSD", price = 1.2, volume = -100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("BTCUSD", false))

        assertLimitOrderMatchingResult(matchingResult, orderBookSize = 1, status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchLimitOrderWithOwnLimitOrder() {
        testBalanceHolderWrapper.updateBalance(1, "EUR", 100.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, price = 1.2, volume = -100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.LeadToNegativeSpread, marketBalance = null)
        assertEquals(1, getOrderBook("EURUSD", false).size)
    }

    @Test
    fun testMatchLimitOrderSellNotEnoughFundsOpposite() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 119.99)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.2, volume = 100.0))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, cancelledSize = 1, remainingVolume = BigDecimal.valueOf(-100.0), status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchLimitOrderBuyNotEnoughFundsOpposite() {
        testBalanceHolderWrapper.updateBalance(2, "EUR", 99.99)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, cancelledSize = 1, status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchLimitOrderBuyNotEnoughFundsOpposite2() {
        testBalanceHolderWrapper.updateBalance(2, "EUR", 100.00)
        testBalanceHolderWrapper.updateReservedBalance(2, "EUR", 99.99)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, cancelledSize = 1, status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchLimitOrderSellNotEnoughFundsOpposite2() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 120.00)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD", 119.99)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.2, volume = 100.0))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, cancelledSize = 1, remainingVolume = BigDecimal.valueOf(-100.0), status = OrderStatus.InOrderBook)
    }

    @Test
    fun testMatchLimitOrderBuyNotEnoughFunds() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 110.00)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.ReservedVolumeGreaterThanBalance, marketBalance = null)
    }

    @Test
    fun testMatchLimitOrderSellNotEnoughFunds() {
        testBalanceHolderWrapper.updateBalance(2, "EUR", 99.00)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.2, volume = 100.0))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.ReservedVolumeGreaterThanBalance, marketBalance = null, remainingVolume = BigDecimal.valueOf(-100.0))
    }

    @Test
    fun testMatchLimitOrderBuyNotEnoughFunds2() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 120.00)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD", 10.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.ReservedVolumeGreaterThanBalance, marketBalance = null)
    }

    @Test
    fun testMatchLimitOrderSellNotEnoughFunds2() {
        testBalanceHolderWrapper.updateBalance(2, "EUR", 100.00)
        testBalanceHolderWrapper.updateReservedBalance(2, "EUR", 1.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.2, volume = 100.0))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.ReservedVolumeGreaterThanBalance, marketBalance = null, remainingVolume = BigDecimal.valueOf(-100.0))
    }

    @Test
    fun testMatchLimitOrderPriceDeviation() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.2, volume = 1.0))
        val limitOrder = buildLimitOrder(walletId = 2, price = 1.1, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true), priceDeviationThreshold = BigDecimal.valueOf(0.08))
        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.TooHighPriceDeviation, marketBalance = null, remainingVolume = BigDecimal.valueOf(-100.0))
    }

    @Test
    fun testMatchLimitOrderBuyOneToOne1() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "uncompleted", walletId = 2, price = 1.19, volume = -100.0, reservedVolume = 100.0))

        val limitOrder = buildLimitOrder(price = 1.21, volume = 91.1)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched,
                marketBalance = BigDecimal.valueOf(891.59),
                remainingVolume = BigDecimal.ZERO,
                skipSize = 0,
                cancelledSize = 0,
                cashMovementsSize = 4,
                marketOrderTradesSize = 1,
                completedLimitOrdersSize = 0,
                limitOrdersReportSize = 1)

        val uncompletedLimitOrder = matchingResult.uncompletedLimitOrder
        assertNotNull(uncompletedLimitOrder)
        assertEquals("uncompleted", uncompletedLimitOrder.id)
        assertEquals(OrderStatus.Processing.name, uncompletedLimitOrder.status)
        assertEquals(BigDecimal.valueOf(-8.9), uncompletedLimitOrder.remainingVolume)
        assertEquals(BigDecimal.valueOf(8.9), uncompletedLimitOrder.reservedLimitVolume!!)
    }

    @Test
    fun testMatchLimitOrderSellOneToOne1() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "uncompleted", price = 1.21, volume = 108.1, reservedVolume = 130.81))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.19, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched,
                marketBalance = BigDecimal.valueOf(900.00),
                remainingVolume = BigDecimal.ZERO,
                skipSize = 0,
                cancelledSize = 0,
                cashMovementsSize = 4,
                marketOrderTradesSize = 1,
                completedLimitOrdersSize = 0,
                limitOrdersReportSize = 1)

        val uncompletedLimitOrder = matchingResult.uncompletedLimitOrder
        assertNotNull(uncompletedLimitOrder)
        assertEquals("uncompleted", uncompletedLimitOrder.id)
        assertEquals(OrderStatus.Processing.name, uncompletedLimitOrder.status)
        assertEquals(BigDecimal.valueOf(8.1), uncompletedLimitOrder.remainingVolume)
        assertEquals(BigDecimal.valueOf(9.81), uncompletedLimitOrder.reservedLimitVolume!!)
    }

    @Test
    fun testMatchLimitOrderBuyOneToOne2() {
        testBalanceHolderWrapper.updateBalance(2, "EUR", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(2, "EUR",  89.1)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "completed", walletId = 2, price = 1.19, volume = -89.1, reservedVolume = 89.1))

        val limitOrder = buildLimitOrder(price = 1.21, volume = 91.1)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Processing,
                marketBalance = BigDecimal.valueOf(893.97),
                remainingVolume = BigDecimal.valueOf(2.0),
                skipSize = 0,
                cancelledSize = 0,
                cashMovementsSize = 4,
                marketOrderTradesSize = 1,
                completedLimitOrdersSize = 1,
                limitOrdersReportSize = 1)

        assertCompletedLimitOrders(matchingResult.completedLimitOrders)

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER,  1, "EUR", BigDecimal.valueOf(89.1), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER,  1, "USD", BigDecimal.valueOf(-106.03), BigDecimal.ZERO)
                ),
                matchingResult.ownCashMovements
        )

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER,  2, "EUR", BigDecimal.valueOf(-89.1), BigDecimal.valueOf(-89.1)),
                        WalletOperation(DEFAULT_BROKER,  2, "USD", BigDecimal.valueOf(106.03), BigDecimal.ZERO)
                ),
                matchingResult.oppositeCashMovements
        )
    }

    @Test
    fun testMatchLimitOrderSellOneToOne2() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD",  110.24)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "completed", price = 1.21, volume = 91.1, reservedVolume = 110.24))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.19, volume = -92.2)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Processing,
                marketBalance = BigDecimal.valueOf(908.9),
                remainingVolume = BigDecimal.valueOf(-1.1),
                skipSize = 0,
                cancelledSize = 0,
                cashMovementsSize = 5,
                marketOrderTradesSize = 1,
                completedLimitOrdersSize = 1,
                limitOrdersReportSize = 1)

        assertCompletedLimitOrders(matchingResult.completedLimitOrders)

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER,  2, "EUR", BigDecimal.valueOf(-91.1), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER,  2, "USD", BigDecimal.valueOf(110.23), BigDecimal.ZERO)
                ),
                matchingResult.ownCashMovements
        )

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER,  1, "EUR", BigDecimal.valueOf(91.1), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER,  1, "USD", BigDecimal.valueOf(-110.23), BigDecimal.valueOf(-110.23)),
                        WalletOperation(DEFAULT_BROKER,  1, "USD", BigDecimal.ZERO, BigDecimal.valueOf(-0.01))
                ),
                matchingResult.oppositeCashMovements
        )
    }

    @Test
    fun testMatchLimitOrderBuyOneToOneFully() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "completed", walletId = 2, price = 1.2, volume = -100.0, reservedVolume = 100.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched, marketBalance = BigDecimal.valueOf(880.0),
                remainingVolume = BigDecimal.ZERO, skipSize = 0, cancelledSize = 0, cashMovementsSize = 4, marketOrderTradesSize = 1, completedLimitOrdersSize = 1,
                limitOrdersReportSize = 1)

        assertCompletedLimitOrders(matchingResult.completedLimitOrders)

    }

    @Test
    fun testMatchLimitOrderSellOneToOneFully() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "completed", price = 1.2, volume = 100.0, reservedVolume = 120.0))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched, marketBalance = BigDecimal.valueOf(900.0),
                remainingVolume = BigDecimal.ZERO, skipSize = 0, cancelledSize = 0, cashMovementsSize = 4, marketOrderTradesSize = 1, completedLimitOrdersSize = 1,
                limitOrdersReportSize = 1)

        assertCompletedLimitOrders(matchingResult.completedLimitOrders)

    }

    @Test
    fun testMatchLimitOrderBuyWithSeveral1() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = -50.0, reservedVolume = 50.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = -50.0, reservedVolume = 50.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched, marketBalance = BigDecimal.valueOf(880.0), remainingVolume = BigDecimal.ZERO, skipSize = 0, cancelledSize = 0, cashMovementsSize = 8, marketOrderTradesSize = 2, completedLimitOrdersSize = 2,
                limitOrdersReportSize = 2)

        assertCompletedLimitOrders(matchingResult.completedLimitOrders, false)
    }

    @Test
    fun testMatchLimitOrderSellWithSeveral1() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.2, volume = 50.0, reservedVolume = 60.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(price = 1.2, volume = 50.0, reservedVolume = 60.0))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched, marketBalance = BigDecimal.valueOf(900.0), remainingVolume = BigDecimal.ZERO,
                skipSize = 0, cancelledSize = 0, cashMovementsSize = 8, marketOrderTradesSize = 2, completedLimitOrdersSize = 2,
                limitOrdersReportSize = 2)

        assertCompletedLimitOrders(matchingResult.completedLimitOrders, false)
    }

    @Test
    fun testMatchLimitOrderBuyWithSeveral2() {
        testBalanceHolderWrapper.updateBalance(3, "EUR", 40.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 40.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.1, volume = -40.0, reservedVolume = 40.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, price = 1.15, volume = -40.0, reservedVolume = 40.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, price = 1.2, volume = -40.0, reservedVolume = 40.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, price = 1.3, volume = -40.0, reservedVolume = 40.0))

        val limitOrder = buildLimitOrder(price = 1.2, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.LeadToNegativeSpread, marketBalance = null)
        assertEquals(4, getOrderBook("EURUSD", false).size)
    }

    @Test
    fun testMatchLimitOrderSellWithSeveral2() {
        testBalanceHolderWrapper.updateBalance(3, "USD", 60.0)
        testBalanceHolderWrapper.updateBalance(4, "USD", 60.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, price = 1.3, volume = 40.0, reservedVolume = 52.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.25, volume = 40.0, reservedVolume = 50.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, price = 1.2, volume = 40.0, reservedVolume = 48.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, price = 1.1, volume = 40.0, reservedVolume = 44.0))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.LeadToNegativeSpread, marketBalance = null, remainingVolume = BigDecimal.valueOf(-100.0))
        assertEquals(4, getOrderBook("EURUSD", true).size)
    }

    @Test
    fun testMatchWithSeveralLimitOrdersOfSameClient1() {
        testBalanceHolderWrapper.updateBalance(1, "BTC", 100.00)
        testBalanceHolderWrapper.updateReservedBalance(1, "BTC",   29.99)
        testBalanceHolderWrapper.updateBalance(1, "BTC", 100.00)
        testBalanceHolderWrapper.updateReservedBalance(1, "BTC",   29.99)
        testBalanceHolderWrapper.updateBalance(2, "USD", 190000.0)
        testBalanceHolderWrapper.updateBalance(3, "BTC", 100.00)
        testBalanceHolderWrapper.updateReservedBalance(3, "BTC",  0.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCUSD", volume = -29.98, price = 6100.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "limit-order-1", assetId = "BTCUSD", volume = -0.01, price = 6105.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, assetId = "BTCUSD", volume = -0.1, price = 6110.0))

        val limitOrder = buildLimitOrder(walletId = 2, assetId = "BTCUSD", price = 6110.0, volume = 30.0)
        Thread.sleep(100)
        val matchingResult = match(limitOrder, getOrderBook("BTCUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched,
                marketBalance = BigDecimal.valueOf(6999.85),
                remainingVolume = BigDecimal.ZERO,
                skipSize = 0,
                cancelledSize = 0,
                cashMovementsSize = 12,
                marketOrderTradesSize = 3,
                completedLimitOrdersSize = 2,
                limitOrdersReportSize = 3)

        assertNotNull(matchingResult.uncompletedLimitOrder)
        assertNotNull(matchingResult.uncompletedLimitOrder!!.lastMatchTime)
        assertEquals(now, matchingResult.uncompletedLimitOrder!!.lastMatchTime!!)
        matchingResult.limitOrdersReport!!.orders.forEach {
            assertNotNull(it.order.lastMatchTime)
            assertEquals(now, it.order.lastMatchTime!!)
        }
        assertEquals(matchingResult.orderCopy.externalId, limitOrder.externalId)
        assertNotNull(limitOrder.lastMatchTime)
        assertEquals(now, limitOrder.lastMatchTime!!)
        assertEquals(BigDecimal.valueOf(-0.09), matchingResult.uncompletedLimitOrder!!.remainingVolume)
    }

    @Test
    fun testMatchWithSeveralLimitOrdersOfSameClient2() {
        testBalanceHolderWrapper.updateBalance(1, "BTC", 100.00)
        testBalanceHolderWrapper.updateReservedBalance(1, "BTC",  29.98)
        testBalanceHolderWrapper.updateBalance(2, "USD", 190000.0)
        testBalanceHolderWrapper.updateBalance(3, "BTC", 100.00)
        testBalanceHolderWrapper.updateReservedBalance(3, "BTC",  0.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(assetId = "BTCUSD", volume = -29.98, price = 6100.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "limit-order-1", assetId = "BTCUSD", volume = -0.01, price = 6105.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, assetId = "BTCUSD", volume = -0.1, price = 6110.0))

        val limitOrder = buildLimitOrder(walletId = 2, assetId = "BTCUSD", price = 6110.0, volume = 30.0)
        val matchingResult = match(limitOrder, getOrderBook("BTCUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched,
                marketBalance = BigDecimal.valueOf(6999.80),
                remainingVolume = BigDecimal.ZERO,
                skipSize = 0,
                cancelledSize = 1,
                cashMovementsSize = 8,
                marketOrderTradesSize = 2,
                completedLimitOrdersSize = 1,
                limitOrdersReportSize = 2)

        assertEquals(1, matchingResult.completedLimitOrders.size)
        assertNotNull(matchingResult.uncompletedLimitOrder)
        assertEquals(BigDecimal.valueOf(-0.08), matchingResult.uncompletedLimitOrder!!.remainingVolume)
    }

    @Test
    fun testTradesAfterMatching() {
        testBalanceHolderWrapper.updateBalance(3, "EUR", 52.33)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.25677, volume = -51.21, reservedVolume = 51.21))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 3, price = 1.30001, volume = -52.33, reservedVolume = 52.33))

        val limitOrder = buildLimitOrder(price = 1.31, volume = 100.0)
        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched, marketBalance = BigDecimal.valueOf(872.21),
                remainingVolume = BigDecimal.ZERO, skipSize = 0, cancelledSize = 0, cashMovementsSize = 8, marketOrderTradesSize = 2, completedLimitOrdersSize = 1,
                limitOrdersReportSize = 2)

        assertNotNull(matchingResult.uncompletedLimitOrder)
    }

    @Test
    fun testMatchLimitOrderSellFullBalance() {
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("LKK1Y", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("LKK", 2))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("LKK1YLKK", "LKK1Y", "LKK", 4))
        initExecutionContext()

        testBalanceHolderWrapper.updateBalance(1, "LKK1Y", 5495.03)
        testBalanceHolderWrapper.updateBalance(2, "LKK", 10000.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "LKK1YLKK", volume = 4.97, price = 1.0105))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "LKK1YLKK", volume = 5500.0, price = 1.0085))

        val matchingResult = match(buildLimitOrder(walletId = 1, assetId = "LKK1YLKK", volume = -5495.03, price = 1.0082,
                fees = buildLimitOrderFeeInstructions(type = FeeType.CLIENT_FEE, takerSize = 0.0009, targetWalletId = 5)), getOrderBook("LKK1YLKK", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.Matched, marketBalance = BigDecimal.ZERO,
                remainingVolume = BigDecimal.ZERO, skipSize = 0, cancelledSize = 0, cashMovementsSize = 10, marketOrderTradesSize = 2, completedLimitOrdersSize = 1,
                limitOrdersReportSize = 2)
    }

    @Test
    fun testMatchLimitOrderWithZeroLatestTrade() {
        testBalanceHolderWrapper.updateBalance(1, "CHF", 1.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "CHF",  1.0)
        testBalanceHolderWrapper.updateBalance(2, "BTC", 0.001)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "BTCCHF", price = 0.231, volume = 1.0, reservedVolume = 0.24))

        val limitOrder = buildLimitOrder(walletId = 2, assetId = "BTCCHF", price = 0.231, volume = -0.001)
        val matchingResult = match(limitOrder, getOrderBook("BTCCHF", true))

        assertLimitOrderMatchingResult(matchingResult, status = OrderStatus.InOrderBook,
                marketBalance = BigDecimal.valueOf(0.001),
                remainingVolume = BigDecimal.valueOf(-0.001),
                skipSize = 1,
                matchedWithZeroLatestTrade = true)
    }
}