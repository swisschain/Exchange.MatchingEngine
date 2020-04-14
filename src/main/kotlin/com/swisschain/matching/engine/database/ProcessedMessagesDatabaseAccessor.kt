package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.deduplication.ProcessedMessage

interface ProcessedMessagesDatabaseAccessor: ReadOnlyProcessedMessagesDatabaseAccessor {
    fun saveProcessedMessage(message: ProcessedMessage)
}