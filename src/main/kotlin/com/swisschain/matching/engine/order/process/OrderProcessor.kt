package com.swisschain.matching.engine.order.process

import com.swisschain.matching.engine.daos.Order
import com.swisschain.matching.engine.order.transaction.ExecutionContext

interface OrderProcessor<T : Order> {
    fun processOrder(order: T, executionContext: ExecutionContext): ProcessedOrder
}