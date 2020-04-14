package com.swisschain.matching.engine.services.validators.business

import com.swisschain.matching.engine.daos.context.CashInOutContext

interface CashInOutOperationBusinessValidator {
    fun performValidation(cashInOutContext: CashInOutContext)
}