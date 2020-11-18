package com.swisschain.matching.engine.config.spring.grpc

import com.swisschain.matching.engine.outgoing.grpc.utils.GrpcEventUtils
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.outgoing.publishers.dispatchers.OutgoingEventDispatcher
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import java.util.concurrent.BlockingDeque
import java.util.concurrent.BlockingQueue

//@Configuration
open class GrpcConfiguration {

    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Bean
    open fun trustedClientsEventsDispatcher(trustedClientsEventsQueue: BlockingDeque<ExecutionEvent>): OutgoingEventDispatcher<ExecutionEvent> {
        return OutgoingEventDispatcher("TrustedClientEventsDispatcher", trustedClientsEventsQueue, trustedQueueNameToQueue())
    }

    @Bean
    open fun clientEventsDispatcher(clientsEventsQueue: BlockingDeque<Event>): OutgoingEventDispatcher<Event> {
        return OutgoingEventDispatcher("ClientEventsDispatcher", clientsEventsQueue, clientQueueNameToQueue())
    }

    @Bean
    open fun trustedQueueNameToQueue(): Map<String, BlockingQueue<ExecutionEvent>> {
        val consumerNameToQueue = HashMap<String, BlockingQueue<ExecutionEvent>>()
        config.me.grpcEndpoints.outgoingTrustedClientsEventsConnections.forEachIndexed { index, grpcConnectionString ->
            val trustedClientsEventConsumerQueueName = GrpcEventUtils.getTrustedClientsEventConsumerQueueName(grpcConnectionString, index)
            val queue = applicationContext.getBean(trustedClientsEventConsumerQueueName) as BlockingQueue<ExecutionEvent>

            consumerNameToQueue[trustedClientsEventConsumerQueueName] = queue
        }

        return consumerNameToQueue
    }

    @Bean
    open fun clientQueueNameToQueue(): Map<String, BlockingQueue<Event>> {
        val consumerNameToQueue = HashMap<String, BlockingQueue<Event>>()
        config.me.grpcEndpoints.outgoingEventsConnections.forEachIndexed { index, grpcConnectionString ->
            val clientsEventConsumerQueueName = GrpcEventUtils.getClientEventConsumerQueueName(grpcConnectionString, index)

            val queue = applicationContext.getBean(clientsEventConsumerQueueName) as BlockingQueue<Event>
            consumerNameToQueue[clientsEventConsumerQueueName] = queue
        }

        return consumerNameToQueue
    }
}

