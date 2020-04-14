package com.swisschain.matching.engine.daos.context

import com.swisschain.matching.engine.daos.TransferOperation
import com.swisschain.matching.engine.deduplication.ProcessedMessage

data class CashTransferContext(
        val messageId: String,
        val transferOperation: TransferOperation,
        val processedMessage: ProcessedMessage)