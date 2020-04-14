package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.holders.StopOrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.order.ExpiryOrdersQueue
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.order.transaction.CurrentTransactionStopOrderBooksHolder
import org.springframework.stereotype.Component
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

@Component
class GenericStopLimitOrderService(private val stopOrdersDatabaseAccessorsHolder: StopOrdersDatabaseAccessorsHolder,
                                   private val expiryOrdersQueue: ExpiryOrdersQueue) : AbstractGenericLimitOrderService<AssetStopOrderBook>() {

    var initialStopOrdersCount = 0
    private val stopLimitOrdersQueues = ConcurrentHashMap<String, ConcurrentHashMap<String, AssetStopOrderBook>>()
    private val stopLimitOrdersMap = HashMap<String, MutableMap<String, LimitOrder>>()
    private val clientStopLimitOrdersMap = HashMap<String, MutableMap<String, MutableList<LimitOrder>>>()

    init {
        update()
    }

    final fun update() {
        stopLimitOrdersMap.values.forEach { brokerOrders ->
            brokerOrders.values.forEach {
                expiryOrdersQueue.removeIfOrderHasExpiryTime(it)
            }
        }
        stopLimitOrdersQueues.clear()
        stopLimitOrdersMap.clear()
        clientStopLimitOrdersMap.clear()

        val stopOrders = stopOrdersDatabaseAccessorsHolder.primaryAccessor.loadStopLimitOrders()
        stopOrders.forEach { order ->
            getOrderBook(order.brokerId, order.assetPairId).addOrder(order)
            addOrder(order)
        }
        initialStopOrdersCount = stopOrders.size
    }

    fun getAllOrderBooks() = stopLimitOrdersQueues

    fun addOrder(order: LimitOrder) {
        stopLimitOrdersMap.getOrPut(order.brokerId) { HashMap() } [order.externalId] = order
        clientStopLimitOrdersMap.getOrPut(order.brokerId) { HashMap() }.getOrPut(order.walletId) { ArrayList() }.add(order)
        expiryOrdersQueue.addIfOrderHasExpiryTime(order)
    }

    override fun addOrders(orders: Collection<LimitOrder>) {
        orders.forEach { order ->
            addOrder(order)
        }
    }

    override fun removeOrdersFromMapsAndSetStatus(orders: Collection<LimitOrder>, status: OrderStatus?, date: Date?) {
        orders.forEach { order ->
            val removedOrder = stopLimitOrdersMap[order.brokerId]?.remove(order.externalId)
            clientStopLimitOrdersMap[order.brokerId]?.get(order.walletId)?.remove(removedOrder)
            expiryOrdersQueue.removeIfOrderHasExpiryTime(order)
            if (removedOrder != null && status != null) {
                removedOrder.updateStatus(status, date!!)
            }
        }
    }

    override fun getOrderBook(brokerId: String, assetPairId: String): AssetStopOrderBook
            = stopLimitOrdersQueues[brokerId]?.get(assetPairId) ?: AssetStopOrderBook(brokerId, assetPairId)

    fun getOrder(brokerId: String, uid: String) = stopLimitOrdersMap[brokerId]?.get(uid)

    override fun setOrderBook(brokerId: String, assetPairId: String, assetOrderBook: AssetStopOrderBook) {
        stopLimitOrdersQueues.getOrPut(brokerId) { ConcurrentHashMap() } [assetPairId] = assetOrderBook
    }

    override fun getLimitOrdersByWalletIdMap(brokerId: String): MutableMap<String, MutableList<LimitOrder>> = clientStopLimitOrdersMap.getOrElse(brokerId) { HashMap() }

    override fun getOrderBooksByAssetPairIdMap(brokerId: String): Map<String, AssetStopOrderBook> = stopLimitOrdersQueues.getOrElse(brokerId) { HashMap() }

    fun createCurrentTransactionOrderBooksHolder() = CurrentTransactionStopOrderBooksHolder(this)

    override fun getTotalSize(brokerId: String?): Int {
        if (brokerId != null) {
            return stopLimitOrdersMap[brokerId]?.size ?: 0
        }
        var result = 0
        stopLimitOrdersMap.values.forEach {
            result += it.size
        }
        return result
    }
}
