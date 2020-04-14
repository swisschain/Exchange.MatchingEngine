package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.Message

interface MessageLogDatabaseAccessor {
    fun log(message: Message)
}