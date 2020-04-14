package com.swisschain.matching.engine.matching

import com.swisschain.matching.engine.daos.LimitOrder

class UpdatedOrderBookAndOrder(val updatedOrderBook: Collection<LimitOrder>,
                               val updatedOrder: LimitOrder?)