package com.swisschain.matching.engine.services.validators.input.impl

import com.swisschain.matching.engine.daos.context.LimitOrderCancelOperationContext
import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderCancelOperationParsedData
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.services.validators.input.LimitOrderCancelOperationInputValidator
import org.springframework.stereotype.Component

@Component
class LimitOrderCancelOperationInputValidatorImpl: LimitOrderCancelOperationInputValidator {
    override fun performValidation(limitOrderCancelOperationParsedData: LimitOrderCancelOperationParsedData) {
        val context = limitOrderCancelOperationParsedData.messageWrapper.context as LimitOrderCancelOperationContext
        validateOrderIds(context)
    }

    private fun validateOrderIds(context: LimitOrderCancelOperationContext) {
        if (context.limitOrderIds.isEmpty()) {
            val errorMessage = "Orders ids list is empty (request id: ${context.uid})"
            throw ValidationException(ValidationException.Validation.GENERIC_VALIDATION_FAILURE, errorMessage)
        }
    }
}