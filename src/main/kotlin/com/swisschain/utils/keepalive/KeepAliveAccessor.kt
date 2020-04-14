package com.swisschain.utils.keepalive

import java.util.Date

internal interface KeepAliveAccessor {
    fun updateKeepAlive(date: Date, note: String?)
}