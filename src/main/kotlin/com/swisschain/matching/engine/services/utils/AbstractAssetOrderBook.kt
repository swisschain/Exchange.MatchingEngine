package com.swisschain.matching.engine.services.utils

import com.swisschain.matching.engine.daos.LimitOrder

abstract class AbstractAssetOrderBook(val brokerId: String, val assetPairId: String) {
    abstract fun copy(): AbstractAssetOrderBook
    abstract fun removeOrder(order: LimitOrder): Boolean
    abstract fun getOrderBook(isBuySide: Boolean): Collection<LimitOrder>
    abstract fun addOrder(order: LimitOrder): Boolean
    fun getBuyOrderBook() = getOrderBook(true)
    fun getSellOrderBook() = getOrderBook(false)
}