package com.swisschain.matching.engine.outgoing.grpc.impl.listeners

import com.swisschain.matching.engine.database.stub.StubMessageLogDatabaseAccessor
import com.swisschain.matching.engine.logging.DatabaseLogger
import com.swisschain.matching.engine.logging.MessageWrapper
import com.swisschain.matching.engine.outgoing.grpc.impl.publishers.GrpcEventPublisher
import com.swisschain.matching.engine.outgoing.grpc.utils.GrpcEventUtils
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.BlockingQueue
import javax.annotation.PostConstruct

//@Component
class ClientsEventListener {
    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @PostConstruct
    fun initGrpcPublisher() {
        config.me.grpcEndpoints.outgoingEventsConnections.forEachIndexed { index, grpcConnectionString ->
            val clientsEventConsumerQueueName = GrpcEventUtils.getClientEventConsumerQueueName(grpcConnectionString, index)
            val queue = applicationContext.getBean(clientsEventConsumerQueueName) as BlockingQueue<Event>
            Thread(GrpcEventPublisher("EventPublisher_$clientsEventConsumerQueueName", queue,
                    clientsEventConsumerQueueName, grpcConnectionString, applicationEventPublisher,
                    DatabaseLogger(
                            StubMessageLogDatabaseAccessor(),
                            applicationContext.getBean(GrpcEventUtils.getDatabaseLogQueueName(grpcConnectionString, index)) as BlockingQueue<MessageWrapper>))).start()
        }
    }
}