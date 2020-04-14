package com.swisschain.utils.alivestatus.database

internal interface AliveStatusDatabaseAccessor {
    fun checkAndLock(): Boolean
    fun keepAlive()
    fun unlock()
}