package com.swisschain.matching.engine.fee

import com.swisschain.matching.engine.daos.WalletOperation

class ReceiptOperationWrapper(receiptOperation: WalletOperation) {
    val baseReceiptOperation = receiptOperation
    var currentReceiptOperation = receiptOperation
}