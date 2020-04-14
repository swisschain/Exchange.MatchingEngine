package com.swisschain.matching.engine.daos.context

import com.swisschain.matching.engine.daos.CashInOutOperation
import com.swisschain.matching.engine.deduplication.ProcessedMessage

data class CashInOutContext(val messageId: String,
                       val processedMessage: ProcessedMessage,
                       val cashInOutOperation: CashInOutOperation)