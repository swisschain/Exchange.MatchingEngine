package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.services.utils.AbstractAssetOrderBook
import java.util.Date

abstract class AbstractGenericLimitOrderService<T : AbstractAssetOrderBook> {

    abstract fun getOrderBook(brokerId: String, assetPairId: String): T
    abstract fun setOrderBook(brokerId: String, assetPairId: String, assetOrderBook: T)
    abstract fun removeOrdersFromMapsAndSetStatus(orders: Collection<LimitOrder>, status: OrderStatus? = null, date: Date? = null)
    abstract fun addOrders(orders: Collection<LimitOrder>)

    fun searchOrders(brokerId: String, walletId: Long?, assetPairId: String?, isBuy: Boolean?): List<LimitOrder> {
        return when {
            walletId != null -> searchClientOrders(brokerId, walletId, assetPairId, isBuy)
            assetPairId != null -> searchAssetPairOrders(brokerId, assetPairId, isBuy)
            else -> getOrderBooksByAssetPairIdMap(brokerId).keys.flatMap { searchAssetPairOrders(brokerId, it, isBuy) }
        }
    }

    private fun searchClientOrders(brokerId: String, walletId: Long, assetPairId: String?, isBuy: Boolean?): List<LimitOrder> {
        val result = mutableListOf<LimitOrder>()
        getLimitOrdersByWalletIdMap(brokerId)[walletId]?.forEach { limitOrder ->
            if ((assetPairId == null || limitOrder.assetPairId == assetPairId) && (isBuy == null || limitOrder.isBuySide() == isBuy)) {
                result.add(limitOrder)
            }
        }
        return result
    }

    private fun searchAssetPairOrders(brokerId: String, assetPairId: String, isBuy: Boolean?): List<LimitOrder> {
        val orderBook = getOrderBooksByAssetPairIdMap(brokerId)[assetPairId] ?: return emptyList()
        val result = mutableListOf<LimitOrder>()
        if (isBuy == null || isBuy) {
            result.addAll(orderBook.getBuyOrderBook())
        }
        if (isBuy == null || !isBuy) {
            result.addAll(orderBook.getSellOrderBook())
        }
        return result
    }

    protected abstract fun getLimitOrdersByWalletIdMap(brokerId: String): Map<Long, Collection<LimitOrder>>
    protected abstract fun getOrderBooksByAssetPairIdMap(brokerId: String): Map<String, T>
    abstract fun getTotalSize(brokerId: String?): Int
}