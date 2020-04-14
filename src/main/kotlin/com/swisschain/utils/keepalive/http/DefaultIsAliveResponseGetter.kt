package com.swisschain.utils.keepalive.http

import com.swisschain.utils.AppVersion

class DefaultIsAliveResponseGetter: IsAliveResponseGetter() {
    private val response = IsAliveResponse(AppVersion.VERSION)
    override fun getResponse(): IsAliveResponse {
        return response
    }
}