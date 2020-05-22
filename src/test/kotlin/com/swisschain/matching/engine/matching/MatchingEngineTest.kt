package com.swisschain.matching.engine.matching

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.daos.CopyWrapper
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.MarketOrder
import com.swisschain.matching.engine.daos.Order
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestFileOrderDatabaseAccessor
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.order.transaction.ExecutionContext
import com.swisschain.matching.engine.order.transaction.ExecutionContextFactory
import com.swisschain.matching.engine.order.utils.TestOrderBookWrapper
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.assertEquals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.Date
import java.util.concurrent.PriorityBlockingQueue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

abstract class MatchingEngineTest {

    @Autowired
    protected lateinit var testDictionariesDatabaseAccessor: TestDictionariesDatabaseAccessor

    @Autowired
    private lateinit var matchingEngine: MatchingEngine

    protected val now = Date()

    @Autowired
    protected lateinit var testDatabaseAccessor: TestFileOrderDatabaseAccessor

    @Autowired
    protected lateinit var testOrderBookWrapper: TestOrderBookWrapper

    @Autowired
    protected lateinit var genericService: GenericLimitOrderService

    @Autowired
    protected lateinit var balancesHolder: BalancesHolder

    @Autowired
    protected lateinit var testBalanceHolderWrapper: TestBalanceHolderWrapper

    @Autowired
    protected lateinit var executionContextFactory: ExecutionContextFactory

    private lateinit var executionContext: ExecutionContext
    @Before
    fun setUp() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 1000.0)
        testBalanceHolderWrapper.updateBalance(2, "EUR", 1000.0)

        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 4))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("CHF", 2))

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCCHF", "BTC", "CHF", 3))

        initExecutionContext()
    }

    protected fun initExecutionContext() {
        executionContext = executionContextFactory.create("messageId", "requestId",
                MessageType.LIMIT_ORDER,
                null,
                testDictionariesDatabaseAccessor.loadAssetPairs()[DEFAULT_BROKER]!!,
                now,
                LoggerFactory.getLogger(MatchingEngineTest::class.java),
                testDictionariesDatabaseAccessor.assets[DEFAULT_BROKER]!!)
    }

    @After
    fun tearDown() {
    }

    protected fun match(order: Order,
                        orderBook: PriorityBlockingQueue<LimitOrder>,
                        priceDeviationThreshold: BigDecimal? = null): MatchingResult {
        return matchingEngine.match(order,
                orderBook,
                "test",
                priceDeviationThreshold = priceDeviationThreshold,
                executionContext = executionContext)
    }

    protected fun assertCompletedLimitOrders(completedLimitOrders: List<CopyWrapper<LimitOrder>>, checkOrderId: Boolean = true) {
        completedLimitOrders.map { it.origin!! }.forEach { completedOrder ->
            if (checkOrderId) {
                assertEquals("completed", completedOrder.id)
            }
            assertEquals(OrderStatus.Matched.name, completedOrder.status)
            assertEquals(BigDecimal.ZERO, completedOrder.remainingVolume)
            assertNotNull(completedOrder.reservedLimitVolume)
            assertEquals(BigDecimal.ZERO, completedOrder.reservedLimitVolume!!)
        }
    }

    protected fun assertMarketOrderMatchingResult(
            matchingResult: MatchingResult,
            marketBalance: BigDecimal? = null,
            marketPrice: BigDecimal? = null,
            status: OrderStatus = OrderStatus.NoLiquidity,
            skipSize: Int = 0,
            cancelledSize: Int = 0,
            cashMovementsSize: Int = 0,
            marketOrderTradesSize: Int = 0,
            completedLimitOrdersSize: Int = 0,
            limitOrdersReportSize: Int = 0,
            orderBookSize: Int = 0
    ) {
        matchingResult.apply()
        executionContext.apply()
        assertTrue { matchingResult.orderCopy is MarketOrder }
        assertEquals(marketPrice, matchingResult.orderCopy.takePrice())
        assertMatchingResult(matchingResult, marketBalance, status, skipSize, cancelledSize, cashMovementsSize,
                marketOrderTradesSize, completedLimitOrdersSize, limitOrdersReportSize, orderBookSize)
    }

    protected fun assertLimitOrderMatchingResult(
            matchingResult: MatchingResult,
            remainingVolume: BigDecimal = BigDecimal.valueOf(100.0),
            marketBalance: BigDecimal? = BigDecimal.valueOf(1000.0),
            status: OrderStatus = OrderStatus.Processing,
            skipSize: Int = 0,
            cancelledSize: Int = 0,
            cashMovementsSize: Int = 0,
            marketOrderTradesSize: Int = 0,
            completedLimitOrdersSize: Int = 0,
            limitOrdersReportSize: Int = 0,
            orderBookSize: Int = 0,
            matchedWithZeroLatestTrade: Boolean = false
    ) {
        matchingResult.apply()
        executionContext.apply()
        assertTrue { matchingResult.orderCopy is LimitOrder }
        val matchedOrder = matchingResult.orderCopy as LimitOrder
        assertEquals(remainingVolume, matchedOrder.remainingVolume)
        assertMatchingResult(matchingResult, marketBalance, status, skipSize, cancelledSize,
                cashMovementsSize, marketOrderTradesSize, completedLimitOrdersSize, limitOrdersReportSize, orderBookSize, matchedWithZeroLatestTrade)
    }

    private fun assertMatchingResult(
            matchingResult: MatchingResult,
            marketBalance: BigDecimal? = BigDecimal.valueOf(1000.0),
            status: OrderStatus = OrderStatus.Processing,
            skipSize: Int = 0,
            cancelledSize: Int = 0,
            cashMovementsSize: Int = 0,
            marketOrderTradesSize: Int = 0,
            completedLimitOrdersSize: Int = 0,
            limitOrdersReportSize: Int = 0,
            orderBookSize: Int = 0,
            matchedWithZeroLatestTrade: Boolean = false
    ) {
        assertEquals(status.name, matchingResult.orderCopy.status)
        if (marketBalance == null) {
            assertNull(matchingResult.marketBalance)
        } else {
            assertNotNull(matchingResult.marketBalance)
            assertEquals(marketBalance, matchingResult.marketBalance!!)
        }
        assertEquals(cancelledSize, matchingResult.cancelledLimitOrders.size)
        assertEquals(cashMovementsSize, matchingResult.ownCashMovements.size + matchingResult.oppositeCashMovements.size)
        assertEquals(marketOrderTradesSize, matchingResult.marketOrderTrades.size)
        assertEquals(completedLimitOrdersSize, matchingResult.completedLimitOrders.size)
        assertEquals(skipSize, matchingResult.skipLimitOrders.size)
        assertEquals(limitOrdersReportSize, matchingResult.limitOrdersReport?.orders?.size ?: 0)
        assertEquals(orderBookSize, matchingResult.orderBook.size)
        assertEquals(matchedWithZeroLatestTrade, matchingResult.matchedWithZeroLatestTrade)
    }

    private fun walletOperationTransform(operation: WalletOperation): WalletOperation =
            WalletOperation(DEFAULT_BROKER, operation.walletId, operation.assetId, operation.amount.stripTrailingZeros(), operation.reservedAmount.stripTrailingZeros())

    protected fun assertCashMovementsEquals(expectedMovements: List<WalletOperation>, actualMovements: List<WalletOperation>) {
        assertEquals(expectedMovements.size, actualMovements.size)
        val expected = expectedMovements.map(this::walletOperationTransform)
        val actual = actualMovements.map(this::walletOperationTransform)
        assertTrue { expected.containsAll(actual) }
    }

    protected fun getOrderBook(assetPairId: String, isBuySide: Boolean): PriorityBlockingQueue<LimitOrder> =
            genericService.getOrderBook(DEFAULT_BROKER, assetPairId).getOrderBook(isBuySide)

}