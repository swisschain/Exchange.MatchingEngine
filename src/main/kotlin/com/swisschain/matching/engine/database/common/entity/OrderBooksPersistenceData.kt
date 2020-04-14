package com.swisschain.matching.engine.database.common.entity

import com.swisschain.matching.engine.daos.LimitOrder
import org.springframework.util.CollectionUtils

class OrderBooksPersistenceData(val orderBooks: Collection<OrderBookPersistenceData>,
                                val ordersToSave: Collection<LimitOrder>,
                                val ordersToRemove: Collection<LimitOrder>) {
    fun isEmpty(): Boolean {
        return CollectionUtils.isEmpty(orderBooks) &&
                CollectionUtils.isEmpty(ordersToSave) &&
                CollectionUtils.isEmpty(ordersToRemove)
    }
}

class OrderBookPersistenceData(val assetPairId: String,
                               val isBuy: Boolean,
                               val orders: Collection<LimitOrder>)

