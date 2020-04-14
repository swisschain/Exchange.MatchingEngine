package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.TransferOperation

interface CashOperationsDatabaseAccessor {
    fun insertTransferOperation(operation: TransferOperation)
}