package com.swisschain.matching.engine.database

class TestMessageSequenceNumberDatabaseAccessor : ReadOnlyMessageSequenceNumberDatabaseAccessor {
    override fun getSequenceNumber() = 0L
}