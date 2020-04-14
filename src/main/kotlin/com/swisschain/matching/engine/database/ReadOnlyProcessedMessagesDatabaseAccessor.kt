package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.deduplication.ProcessedMessage

interface ReadOnlyProcessedMessagesDatabaseAccessor {
    fun get(): Set<ProcessedMessage>
}