package com.swisschain.matching.engine.database

interface CashOperationIdDatabaseAccessor {
    fun isAlreadyProcessed(type: String, id: String): Boolean
}