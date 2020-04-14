package com.swisschain.matching.engine.order.process.context

import com.swisschain.matching.engine.daos.MarketOrder
import com.swisschain.matching.engine.order.transaction.ExecutionContext

class MarketOrderExecutionContext(order: MarketOrder, executionContext: ExecutionContext)
    : OrderExecutionContext<MarketOrder>(order, executionContext)