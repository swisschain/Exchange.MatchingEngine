package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.LimitOrder

interface OrderBookDatabaseAccessor {
    fun loadLimitOrders(): List<LimitOrder>
    fun updateOrderBook(asset: String, isBuy: Boolean, orderBook: Collection<LimitOrder>)
}