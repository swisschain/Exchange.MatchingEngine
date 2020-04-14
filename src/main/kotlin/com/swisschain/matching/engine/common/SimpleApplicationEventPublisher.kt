package com.swisschain.matching.engine.common

interface SimpleApplicationEventPublisher<T> {
    fun publishEvent(event: T)
}