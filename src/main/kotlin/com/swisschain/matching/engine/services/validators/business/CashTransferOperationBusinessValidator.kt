package com.swisschain.matching.engine.services.validators.business

import com.swisschain.matching.engine.daos.context.CashTransferContext

interface CashTransferOperationBusinessValidator {
    fun performValidation(cashTransferContext: CashTransferContext)
}