package com.swisschain.matching.engine.outgoing.grpc.impl.listeners

import com.swisschain.matching.engine.outgoing.grpc.impl.publishers.GrpcEventPublisher
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import com.swisschain.matching.engine.outgoing.publishers.events.PublisherFailureEvent
import com.swisschain.matching.engine.outgoing.publishers.events.PublisherReadyEvent
import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.matching.engine.utils.monitoring.HealthMonitorEvent
import com.swisschain.matching.engine.utils.monitoring.MonitoredComponent
import com.swisschain.utils.logging.MetricsLogger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import java.util.concurrent.BlockingDeque
import javax.annotation.PostConstruct

//@Component
class OrderBookListener {
    @Volatile
    private var failed = false

    @Autowired
    private lateinit var outgoingOrderBookQueue: BlockingDeque<OrderBookEvent>

    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    companion object {
        private val LOGGER = LoggerFactory.getLogger(OrderBookListener::class.java.name)
        private val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    @PostConstruct
    fun initGrpcPublisher() {
        Thread(GrpcEventPublisher("TrustedClientsEventPublisher_OrderBookQueue", outgoingOrderBookQueue,
                "OrderBookQueue", config.me.grpcEndpoints.orderBooksConnection, applicationEventPublisher,
                null)).start()
    }

    @EventListener
    fun onFailure(publisherFailureEvent: PublisherFailureEvent<*>) {
        if(publisherFailureEvent.publisherName == OrderBookListener::class.java.simpleName) {
            failed = true
            val message = "Order book publisher: ${publisherFailureEvent.publisherName} failed"
            LOGGER.error(message)
            METRICS_LOGGER.logError(message)
            publisherFailureEvent.failedEvent?.let {
                outgoingOrderBookQueue.putFirst(it as OrderBookEvent)
            }
            applicationEventPublisher.publishEvent(HealthMonitorEvent(false, MonitoredComponent.PUBLISHER, publisherFailureEvent.publisherName))
        }
    }

    @EventListener
    fun onReady(publisherReadyEvent: PublisherReadyEvent) {
        if (publisherReadyEvent.publisherName == OrderBookListener::class.java.simpleName && failed) {
            failed = false
            val message = "Order book publisher: ${publisherReadyEvent.publisherName}, recovered"
            LOGGER.warn(message)
            METRICS_LOGGER.logWarning(message)
            applicationEventPublisher.publishEvent(HealthMonitorEvent(true, MonitoredComponent.PUBLISHER, publisherReadyEvent.publisherName))
        }
    }
}