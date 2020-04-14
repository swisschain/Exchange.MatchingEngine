package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.LimitOrder

interface StopOrderBookDatabaseAccessor {
    fun loadStopLimitOrders(): List<LimitOrder>
    fun updateStopOrderBook(assetPairId: String, isBuy: Boolean, orderBook: Collection<LimitOrder>)
}