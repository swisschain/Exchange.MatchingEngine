package com.swisschain.matching.engine.services.validators.business

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.context.LimitOrderCancelOperationContext
import com.swisschain.matching.engine.daos.order.LimitOrderType

interface LimitOrderCancelOperationBusinessValidator {
    fun performValidation(typeToOrder: Map<LimitOrderType, List<LimitOrder>>, context: LimitOrderCancelOperationContext)
}