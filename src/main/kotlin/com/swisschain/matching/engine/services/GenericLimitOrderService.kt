package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.holders.OrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.order.ExpiryOrdersQueue
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.order.OrderStatus.Cancelled
import com.swisschain.matching.engine.order.transaction.CurrentTransactionOrderBooksHolder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

@Component
class GenericLimitOrderService @Autowired constructor(private val orderBookDatabaseAccessorHolder: OrdersDatabaseAccessorsHolder,
                                                      private val expiryOrdersQueue: ExpiryOrdersQueue) : AbstractGenericLimitOrderService<AssetOrderBook>() {

    //broker -> asset -> orderBook
    private val limitOrdersQueues = ConcurrentHashMap<String, ConcurrentHashMap<String, AssetOrderBook>>()
    private val limitOrdersMap = HashMap<String, MutableMap<String, LimitOrder>>()
    private val clientLimitOrdersMap = HashMap<String, MutableMap<String, MutableList<LimitOrder>>>()
    var initialOrdersCount = 0

    init {
        update()
    }

    final fun update() {
        limitOrdersMap.values.forEach { brokerOrders ->
            brokerOrders.values.forEach {
                expiryOrdersQueue.removeIfOrderHasExpiryTime(it)
            }
        }
        limitOrdersQueues.clear()
        limitOrdersMap.clear()
        clientLimitOrdersMap.clear()
        val orders = orderBookDatabaseAccessorHolder.primaryAccessor.loadLimitOrders()
        for (order in orders) {
            addToOrderBook(order)
        }
        initialOrdersCount = orders.size
    }

    private fun addToOrderBook(order: LimitOrder) {
        val orderBook = limitOrdersQueues.getOrPut(order.brokerId) { ConcurrentHashMap() }
                .getOrPut(order.assetPairId) { AssetOrderBook(order.brokerId, order.assetPairId) }
        orderBook.addOrder(order)
        addOrder(order)
    }

    fun addOrder(order: LimitOrder) {
        limitOrdersMap.getOrPut(order.brokerId) { HashMap() } [order.externalId] = order
        clientLimitOrdersMap.getOrPut(order.brokerId) { HashMap() }.getOrPut(order.walletId) { ArrayList() }.add(order)
        expiryOrdersQueue.addIfOrderHasExpiryTime(order)
    }

    override fun addOrders(orders: Collection<LimitOrder>) {
        orders.forEach { order ->
            addOrder(order)
        }
    }

    fun getAllOrderBooks() = limitOrdersQueues

    fun getAllOrderBooks(brokerId: String) = limitOrdersQueues[brokerId]

    override fun getOrderBook(brokerId: String, assetPairId: String) = limitOrdersQueues[brokerId]?.get(assetPairId) ?: AssetOrderBook(brokerId, assetPairId)

    override fun setOrderBook(brokerId: String, assetPairId: String, assetOrderBook: AssetOrderBook) {
        limitOrdersQueues.getOrPut(brokerId) { ConcurrentHashMap() } [assetPairId] = assetOrderBook
    }

    override fun getLimitOrdersByWalletIdMap(brokerId: String): Map<String, MutableList<LimitOrder>> = clientLimitOrdersMap.getOrElse(brokerId) { HashMap() }

    override fun getOrderBooksByAssetPairIdMap(brokerId: String): Map<String, AssetOrderBook> = limitOrdersQueues.getOrElse(brokerId) { HashMap() }

    fun getOrder(brokerId: String, uid: String) = limitOrdersMap[brokerId]?.get(uid)

    fun cancelLimitOrder(brokerId: String, date: Date, uid: String, removeFromClientMap: Boolean = false): LimitOrder? {
        val order = limitOrdersMap[brokerId]?.remove(uid) ?: return null
        expiryOrdersQueue.removeIfOrderHasExpiryTime(order)

        if (removeFromClientMap) {
            removeFromClientMap(brokerId, uid)
        }

        getOrderBook(brokerId, order.assetPairId).removeOrder(order)
        order.updateStatus(Cancelled, date)
        return order
    }

    private fun removeFromClientMap(brokerId: String, uid: String): Boolean {
        val order: LimitOrder = clientLimitOrdersMap[brokerId]?.values?.firstOrNull { it.any { it.externalId == uid } }?.firstOrNull { it.externalId == uid } ?: return false
        return clientLimitOrdersMap[brokerId]?.get(order.walletId)?.remove(order) ?: false
    }

    override fun removeOrdersFromMapsAndSetStatus(orders: Collection<LimitOrder>, status: OrderStatus?, date: Date?) {
        orders.forEach { order ->
            val removedOrder = limitOrdersMap[order.brokerId]?.remove(order.externalId)
            clientLimitOrdersMap[order.brokerId]?.get(order.walletId)?.remove(removedOrder)
            expiryOrdersQueue.removeIfOrderHasExpiryTime(order)
            if (removedOrder != null && status != null) {
                removedOrder.updateStatus(status, date!!)
            }
        }
    }

    fun createCurrentTransactionOrderBooksHolder() = CurrentTransactionOrderBooksHolder(this)

    override fun getTotalSize(brokerId: String?): Int {
        if (brokerId != null) {
            return limitOrdersMap[brokerId]?.size ?: 0
        }
        var result = 0
        limitOrdersMap.values.forEach {
            result += it.size
        }
        return result
    }
}
