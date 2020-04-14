package com.swisschain.matching.engine.services.validators.input

import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderCancelOperationParsedData

interface LimitOrderCancelOperationInputValidator {
    fun performValidation(limitOrderCancelOperationParsedData: LimitOrderCancelOperationParsedData)
}