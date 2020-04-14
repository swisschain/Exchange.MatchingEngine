package com.swisschain.matching.engine.order.transaction

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.database.common.entity.OrderBookPersistenceData
import com.swisschain.matching.engine.database.common.entity.OrderBooksPersistenceData
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.services.AssetStopOrderBook
import com.swisschain.matching.engine.services.GenericStopLimitOrderService
import java.math.BigDecimal
import java.util.Date

class CurrentTransactionStopOrderBooksHolder(private val genericStopLimitOrderService: GenericStopLimitOrderService)
    : AbstractTransactionOrderBooksHolder<AssetStopOrderBook, GenericStopLimitOrderService>(genericStopLimitOrderService) {

    fun pollStopOrderToExecute(brokerId: String,
                               assetPairId: String,
                               bestBidPrice: BigDecimal,
                               bestAskPrice: BigDecimal,
                               date: Date): LimitOrder? {
        return pollStopOrderToExecute(brokerId, assetPairId, bestBidPrice, false, date)
                ?: pollStopOrderToExecute(brokerId, assetPairId, bestAskPrice, true, date)
    }

    private fun pollStopOrderToExecute(brokerId: String,
                                       assetPairId: String,
                                       bestOppositePrice: BigDecimal,
                                       isBuySide: Boolean,
                                       date: Date): LimitOrder? {
        if (bestOppositePrice <= BigDecimal.ZERO) {
            return null
        }
        val stopOrderBook = getChangedCopyOrOriginalOrderBook(brokerId, assetPairId)
        var order: LimitOrder?
        var orderPrice: BigDecimal? = null
        order = stopOrderBook.getOrder(bestOppositePrice, isBuySide, true)
        if (order != null) {
            orderPrice = order.lowerPrice!!
        } else {
            order = stopOrderBook.getOrder(bestOppositePrice, isBuySide, false)
            if (order != null) {
                orderPrice = order.upperPrice!!
            }
        }
        if (order == null) {
            return null
        }
        addRemovedOrders(listOf(order), completedOrders)
        getChangedOrderBookCopy(brokerId, assetPairId).removeOrder(order)
        val orderCopy = order.copy()
        orderCopy.price = orderPrice!!
        orderCopy.updateStatus(OrderStatus.Executed, date)
        return orderCopy
    }

    override fun applySpecificPart(date: Date) {
        assetOrderBookCopiesByAssetPairId.forEach { (assetPairId, orderBook) ->
            genericStopLimitOrderService.setOrderBook(orderBook.brokerId, assetPairId, orderBook)
        }
    }

    override fun getPersistenceData(): OrderBooksPersistenceData {
        val orderBookPersistenceDataList = mutableListOf<OrderBookPersistenceData>()
        val ordersToSave = mutableListOf<LimitOrder>()
        val ordersToRemove = ArrayList<LimitOrder>(completedOrders.size + cancelledOrders.size + replacedOrders.size)

        ordersToRemove.addAll(completedOrders)
        ordersToRemove.addAll(cancelledOrders)
        ordersToRemove.addAll(replacedOrders)

        assetOrderBookCopiesByAssetPairId.forEach { assetPairId, orderBook ->
            if (changedBuySides.contains(assetPairId)) {
                orderBookPersistenceDataList.add(OrderBookPersistenceData(assetPairId, true, orderBook.getOrderBook(true)))
            }
            if (changedSellSides.contains(assetPairId)) {
                orderBookPersistenceDataList.add(OrderBookPersistenceData(assetPairId, false, orderBook.getOrderBook(false)))
            }
        }

        ordersToSave.addAll(newOrdersByExternalId.values)
        return OrderBooksPersistenceData(orderBookPersistenceDataList, ordersToSave, ordersToRemove)
    }

}