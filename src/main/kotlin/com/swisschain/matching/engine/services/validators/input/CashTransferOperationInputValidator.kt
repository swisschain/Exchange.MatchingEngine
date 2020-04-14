package com.swisschain.matching.engine.services.validators.input

import com.swisschain.matching.engine.incoming.parsers.data.CashTransferParsedData

interface CashTransferOperationInputValidator {
    fun performValidation(cashTransferParsedData: CashTransferParsedData)
}