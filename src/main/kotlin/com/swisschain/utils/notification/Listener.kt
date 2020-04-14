package com.swisschain.utils.notification

interface Listener<T> {
    fun subscribe(subscriber: Subscriber<T>)
    fun unsubscribe(subscriber: Subscriber<T>)
}