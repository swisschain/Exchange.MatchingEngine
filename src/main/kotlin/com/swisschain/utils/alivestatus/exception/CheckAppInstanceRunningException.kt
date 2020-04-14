package com.swisschain.utils.alivestatus.exception

import com.swisschain.utils.alivestatus.daos.AliveStatus

class CheckAppInstanceRunningException internal constructor(message: String, private val appInstance: AliveStatus? = null) : Exception(message) {

    override val message: String?
        get() = "${super.message} ${appInstance ?: ""}"
}