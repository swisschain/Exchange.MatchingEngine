package com.swisschain.matching.engine.database

interface ReadOnlyMessageSequenceNumberDatabaseAccessor {
    fun getSequenceNumber(): Long
}