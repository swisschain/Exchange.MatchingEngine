package com.swisschain.matching.engine.services.validators.input

import com.swisschain.matching.engine.incoming.parsers.data.CashInOutParsedData

interface CashInOutOperationInputValidator {
    fun performValidation(cashInOutParsedData: CashInOutParsedData)
}