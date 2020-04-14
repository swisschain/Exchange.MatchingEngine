package com.swisschain.matching.engine.order.utils

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.database.TestOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.TestStopOrderBookDatabaseAccessor
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.services.GenericStopLimitOrderService

class TestOrderBookWrapper(private val genericLimitOrderService:  GenericLimitOrderService,
                           private val testOrderBookDatabaseAccessor: TestOrderBookDatabaseAccessor,
                           private val genericStopLimitOrderService: GenericStopLimitOrderService,
                           private val stopOrderBookDatabaseAccessor: TestStopOrderBookDatabaseAccessor) {


    fun addLimitOrder(limitOrder: LimitOrder) {
        testOrderBookDatabaseAccessor.addLimitOrder(limitOrder)

        genericLimitOrderService.addOrder(limitOrder)
        val orderBook = genericLimitOrderService.getOrderBook(DEFAULT_BROKER, limitOrder.assetPairId)
        orderBook.addOrder(limitOrder)
        genericLimitOrderService.setOrderBook(DEFAULT_BROKER,limitOrder.assetPairId, orderBook)
    }

    fun addStopLimitOrder(limitOrder: LimitOrder) {
        stopOrderBookDatabaseAccessor.addStopLimitOrder(limitOrder)

        genericStopLimitOrderService.addOrder(limitOrder)
        val orderBook = genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER,limitOrder.assetPairId)
        orderBook.addOrder(limitOrder)
        genericStopLimitOrderService.setOrderBook(DEFAULT_BROKER,limitOrder.assetPairId, orderBook)
    }
}