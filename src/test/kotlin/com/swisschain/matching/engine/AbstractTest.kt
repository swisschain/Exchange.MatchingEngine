package com.swisschain.matching.engine

import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.daos.wallet.Wallet
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestFileOrderDatabaseAccessor
import com.swisschain.matching.engine.database.TestPersistenceManager
import com.swisschain.matching.engine.database.TestStopOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.TestWalletDatabaseAccessor
import com.swisschain.matching.engine.database.cache.ApplicationSettingsCache
import com.swisschain.matching.engine.database.cache.AssetPairsCache
import com.swisschain.matching.engine.database.cache.AssetsCache
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.OrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.holders.StopOrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.order.utils.TestOrderBookWrapper
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.services.CashInOutOperationService
import com.swisschain.matching.engine.services.CashTransferOperationService
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.services.GenericStopLimitOrderService
import com.swisschain.matching.engine.services.LimitOrderCancelService
import com.swisschain.matching.engine.services.LimitOrderMassCancelService
import com.swisschain.matching.engine.services.MarketOrderService
import com.swisschain.matching.engine.services.MultiLimitOrderService
import com.swisschain.matching.engine.services.SingleLimitOrderService
import com.swisschain.matching.engine.utils.NumberUtils
import com.swisschain.matching.engine.utils.assertEquals
import com.swisschain.matching.engine.utils.order.MinVolumeOrderCanceller
import org.junit.After
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

abstract class AbstractTest {

    companion object {
        public val DEFAULT_BROKER = "Broker1"
    }

    @Autowired
    lateinit var balancesHolder: BalancesHolder

    @Autowired
    protected lateinit var ordersDatabaseAccessorsHolder: OrdersDatabaseAccessorsHolder

    @Autowired
    protected lateinit var stopOrdersDatabaseAccessorsHolder: StopOrdersDatabaseAccessorsHolder

    @Autowired
    protected lateinit var testWalletDatabaseAccessor: TestWalletDatabaseAccessor
    protected lateinit var stopOrderDatabaseAccessor: TestStopOrderBookDatabaseAccessor

    @Autowired
    private lateinit var assetsCache: AssetsCache

    @Autowired
    protected lateinit var applicationSettingsHolder: ApplicationSettingsHolder

    @Autowired
    protected lateinit var applicationSettingsCache: ApplicationSettingsCache

    @Autowired
    protected lateinit var testBalanceHolderWrapper: TestBalanceHolderWrapper

    @Autowired
    protected lateinit var testDictionariesDatabaseAccessor: TestDictionariesDatabaseAccessor

    @Autowired
    protected lateinit var assetPairsCache: AssetPairsCache

    @Autowired
    protected lateinit var persistenceManager: TestPersistenceManager

    @Autowired
    protected lateinit var testOrderDatabaseAccessor: TestFileOrderDatabaseAccessor

    @Autowired
    protected lateinit var genericLimitOrderService: GenericLimitOrderService

    @Autowired
    protected lateinit var singleLimitOrderService: SingleLimitOrderService

    @Autowired
    protected lateinit var multiLimitOrderService: MultiLimitOrderService

    @Autowired
    protected lateinit var marketOrderService: MarketOrderService

    @Autowired
    protected lateinit var minVolumeOrderCanceller: MinVolumeOrderCanceller

    @Autowired
    protected lateinit var outgoingOrderBookQueue: BlockingQueue<OrderBookEvent>

    @Autowired
    protected lateinit var genericStopLimitOrderService: GenericStopLimitOrderService

    @Autowired
    protected lateinit var testOrderBookWrapper: TestOrderBookWrapper

    @Autowired
    protected lateinit var limitOrderCancelService: LimitOrderCancelService

    @Autowired
    protected lateinit var cashTransferOperationsService: CashTransferOperationService

    @Autowired
    protected lateinit var clientsEventsQueue: BlockingQueue<Event>

    @Autowired
    protected lateinit var trustedClientsEventsQueue: BlockingQueue<ExecutionEvent>

    @Autowired
    protected lateinit var limitOrderMassCancelService: LimitOrderMassCancelService

    @Autowired
    protected lateinit var cashInOutOperationService: CashInOutOperationService

    protected open fun initServices() {
        stopOrderDatabaseAccessor = stopOrdersDatabaseAccessorsHolder.primaryAccessor as TestStopOrderBookDatabaseAccessor
        clearMessageQueues()
        assetsCache.update()
        assetPairsCache.update()
        applicationSettingsCache.update()
        applicationSettingsHolder.update()
    }

    protected fun clearMessageQueues() {
        clientsEventsQueue.clear()
        trustedClientsEventsQueue.clear()
        outgoingOrderBookQueue.clear()
    }

    protected fun assertOrderBookSize(assetPairId: String, isBuySide: Boolean, size: Int) {
        assertEquals(size, testOrderDatabaseAccessor.getOrders(assetPairId, isBuySide).size)
        assertEquals(size, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, assetPairId).getOrderBook(isBuySide).size)

        // check cache orders map size
        val allWalletIds = testWalletDatabaseAccessor.loadWallets()[DEFAULT_BROKER]!!.keys
        assertEquals(size, allWalletIds.sumBy { genericLimitOrderService.searchOrders(DEFAULT_BROKER, it, assetPairId, isBuySide).size })
    }

    protected fun assertStopOrderBookSize(assetPairId: String, isBuySide: Boolean, size: Int) {
        assertEquals(size, stopOrderDatabaseAccessor.getStopOrders(assetPairId, isBuySide).size)
        assertEquals(size, genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, assetPairId).getOrderBook(isBuySide).size)

        // check cache orders map size
        val allWalletIds = testWalletDatabaseAccessor.loadWallets()[DEFAULT_BROKER]!!.keys
        assertEquals(size, allWalletIds.sumBy { genericStopLimitOrderService.searchOrders(DEFAULT_BROKER, it, assetPairId, isBuySide).size })
    }

    protected fun assertBalance(walletId: String, assetId: String, balance: Double? = null, reserved: Double? = null) {
        if (balance != null) {
            assertEquals(BigDecimal.valueOf(balance), balancesHolder.getBalance(DEFAULT_BROKER, walletId, assetId))
            assertEquals(BigDecimal.valueOf(balance), testWalletDatabaseAccessor.getBalance(walletId, assetId))
        }
        if (reserved != null) {
            assertEquals(BigDecimal.valueOf(reserved), balancesHolder.getReservedBalance(DEFAULT_BROKER, walletId, assetId))
            assertEquals(BigDecimal.valueOf(reserved), testWalletDatabaseAccessor.getReservedBalance(walletId, assetId))
        }
    }

    @After
    open fun tearDown() {
        assertEqualsDbAndCacheLimitOrders()
        assertEqualsDbAndCacheStopLimitOrders()
        assertEqualsDbAndCacheBalances()
    }

    private fun assertEqualsDbAndCacheLimitOrders() {
        val primaryDbOrders = ordersDatabaseAccessorsHolder.primaryAccessor.loadLimitOrders()
        val secondaryDbOrders = ordersDatabaseAccessorsHolder.secondaryAccessor!!.loadLimitOrders()
        val cacheOrders = genericLimitOrderService.getAllOrderBooks().getOrDefault(DEFAULT_BROKER, ConcurrentHashMap()).values.flatMap {
            val orders = mutableListOf<LimitOrder>()
            orders.addAll(it.getOrderBook(false))
            orders.addAll(it.getOrderBook(true))
            orders
        }
        assertEqualsOrderLists(primaryDbOrders, cacheOrders)
        assertEqualsOrderLists(secondaryDbOrders, cacheOrders)
    }

    private fun assertEqualsDbAndCacheStopLimitOrders() {
        val primaryDbOrders = stopOrdersDatabaseAccessorsHolder.primaryAccessor.loadStopLimitOrders()
        val secondaryDbOrders = stopOrdersDatabaseAccessorsHolder.secondaryAccessor!!.loadStopLimitOrders()
        val cacheOrders = genericStopLimitOrderService.getAllOrderBooks().getOrDefault(DEFAULT_BROKER, ConcurrentHashMap()).values.flatMap {
            val orders = mutableListOf<LimitOrder>()
            orders.addAll(it.getOrderBook(false))
            orders.addAll(it.getOrderBook(true))
            orders
        }
        assertEqualsOrderLists(primaryDbOrders, cacheOrders)
        assertEqualsOrderLists(secondaryDbOrders, cacheOrders)
    }

    private fun assertEqualsOrderLists(orders1: Collection<LimitOrder>, orders2: Collection<LimitOrder>) {
        val ordersMap1 = orders1.groupBy { it.id }.mapValues { it.value.first() }
        val ordersMap2 = orders2.groupBy { it.id }.mapValues { it.value.first() }
        assertEquals(ordersMap1.size, ordersMap2.size)
        ordersMap1.forEach { id, order1 ->
            val order2 = ordersMap2[id]
            assertNotNull(order2)
            assertEqualsOrders(order1, order2)
        }
    }

    private fun assertEqualsOrders(order1: LimitOrder, order2: LimitOrder) {
        assertEquals(order1.id, order2.id)
        assertEquals(order1.externalId, order2.externalId)
        assertEquals(order1.status, order2.status)
        assertEquals(order1.statusDate, order2.statusDate)
        assertEquals(order1.remainingVolume, order2.remainingVolume)
        assertEquals(order1.lastMatchTime, order2.lastMatchTime)
        assertEquals(order1.reservedLimitVolume, order2.reservedLimitVolume)
        assertEquals(order1.price, order2.price)
    }

    private fun assertEqualsDbAndCacheBalances() {
        val primaryDbWallets = testWalletDatabaseAccessor.loadWallets().getOrDefault(DEFAULT_BROKER, HashMap()).toMap()
        val cacheWallets = balancesHolder.wallets.getOrDefault(DEFAULT_BROKER, HashMap()).toMap()
        checkBalances(primaryDbWallets, cacheWallets)
    }

    private fun checkBalances(wallets1: Map<String, Wallet>, wallets2: Map<String, Wallet>) {
        val balances1ByClientAndAsset = balancesByClientAndAsset(wallets1)
        val balances2ByClientAndAsset = balancesByClientAndAsset(wallets2)

        assertEquals(wallets1.size, wallets2.size)
        balances1ByClientAndAsset.forEach { id, assetBalance1 ->
            val assetBalance2 = balances2ByClientAndAsset[id] ?: throw Exception("Balances lists are different")
            assertEqualsBalances(assetBalance1, assetBalance2)
        }
    }

    private fun balancesByClientAndAsset(wallets: Map<String, Wallet>): Map<String, AssetBalance> {
        return wallets.values.flatMap { wallet ->
            wallet.balances.values.filter { assetBalance ->
                NumberUtils.equalsIgnoreScale(assetBalance.balance, BigDecimal.ZERO)
            }
        }.groupBy { assetBalance ->
            assetBalance.walletId + ";" + assetBalance.asset
        }.mapValues { it.value.single() }
    }

    private fun assertEqualsBalances(balance1: AssetBalance, balance2: AssetBalance) {
        assertEquals(balance1.asset, balance2.asset)
        assertEquals(balance1.walletId, balance2.walletId)
        assertEquals(balance1.balance.toDouble(), balance2.balance.toDouble())
        assertEquals(balance1.reserved.toDouble(), balance2.reserved.toDouble())
    }

    protected fun assertEventBalanceUpdate(walletId: String,
                                           assetId: String,
                                           oldBalance: String?,
                                           newBalance: String?,
                                           oldReserved: String?,
                                           newReserved: String?,
                                           balanceUpdates: Collection<BalanceUpdate>) {
        val balanceUpdate = balanceUpdates.single { it.walletId == walletId && it.assetId == assetId }
        assertEquals(oldBalance, balanceUpdate.oldBalance)
        assertEquals(newBalance, balanceUpdate.newBalance)
        assertEquals(oldReserved, balanceUpdate.oldReserved)
        assertEquals(newReserved, balanceUpdate.newReserved)
    }
}