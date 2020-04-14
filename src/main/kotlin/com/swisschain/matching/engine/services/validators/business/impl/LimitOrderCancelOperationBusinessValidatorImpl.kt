package com.swisschain.matching.engine.services.validators.business.impl

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.context.LimitOrderCancelOperationContext
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.services.validators.business.LimitOrderCancelOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import org.springframework.stereotype.Component

@Component
class LimitOrderCancelOperationBusinessValidatorImpl : LimitOrderCancelOperationBusinessValidator {
    override fun performValidation(typeToOrder: Map<LimitOrderType, List<LimitOrder>>, context: LimitOrderCancelOperationContext) {
        validateOrdersAreFound(typeToOrder, context)
    }

    private fun validateOrdersAreFound(typeToOrder: Map<LimitOrderType, List<LimitOrder>>, context: LimitOrderCancelOperationContext) {
        if (typeToOrder.isEmpty()) {
            throw ValidationException(ValidationException.Validation.LIMIT_ORDER_NOT_FOUND, "Unable to find order ids: ${context.limitOrderIds}")
        }
    }
}