package com.swisschain.matching.engine.order.process.common

interface LimitOrdersCancelExecutor {

    fun cancelOrdersAndApply(request: CancelRequest): Boolean
}