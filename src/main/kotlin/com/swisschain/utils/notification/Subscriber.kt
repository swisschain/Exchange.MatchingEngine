package com.swisschain.utils.notification

interface Subscriber<T> {
    fun notify(message: T)
}