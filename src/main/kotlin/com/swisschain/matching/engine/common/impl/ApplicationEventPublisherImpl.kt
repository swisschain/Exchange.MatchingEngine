package com.swisschain.matching.engine.common.impl

import com.swisschain.matching.engine.common.QueueConsumer
import com.swisschain.matching.engine.common.SimpleApplicationEventPublisher
import java.util.Optional
import java.util.concurrent.BlockingQueue

class ApplicationEventPublisherImpl<T>(private val queue: BlockingQueue<T>,
                                       queueConsumers: Optional<List<QueueConsumer<T>?>>): SimpleApplicationEventPublisher<T> {
    private val listeners: List<QueueConsumer<T>> = queueConsumers.orElseGet{ emptyList() }
            .filter { it != null }
            .map { it!! }

    override fun publishEvent(event: T) {
        if (listeners.isEmpty()) {
            return
        }

        queue.put(event)
    }
}