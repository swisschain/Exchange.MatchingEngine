package com.swisschain.matching.engine.notification

import java.util.concurrent.BlockingQueue

abstract class AbstractQueueWrapper<V> {

    abstract fun getProcessingQueue(): BlockingQueue<V>

    fun getCount(): Int {
        return getProcessingQueue().size
    }

    fun getQueue(): BlockingQueue<V> {
        return getProcessingQueue()
    }

    fun clear() {
        getProcessingQueue().clear()
    }
}