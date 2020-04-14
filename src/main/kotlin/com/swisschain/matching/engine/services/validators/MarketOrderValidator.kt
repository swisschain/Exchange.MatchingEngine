package com.swisschain.matching.engine.services.validators

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.MarketOrder
import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import java.util.concurrent.PriorityBlockingQueue


interface MarketOrderValidator {
    fun performValidation(order: MarketOrder, orderBook: PriorityBlockingQueue<LimitOrder>, feeInstructions: List<NewFeeInstruction>?)
}