package com.swisschain.matching.engine.order.process

import com.swisschain.matching.engine.daos.LimitOrder

class ProcessedOrder(val order: LimitOrder, val accepted: Boolean, val reason: String? = null)