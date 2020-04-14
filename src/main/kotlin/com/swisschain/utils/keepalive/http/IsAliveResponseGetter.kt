package com.swisschain.utils.keepalive.http

abstract class IsAliveResponseGetter {
    abstract fun getResponse(): IsAliveResponse
}