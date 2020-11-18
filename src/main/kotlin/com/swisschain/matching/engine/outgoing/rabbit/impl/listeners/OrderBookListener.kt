package com.swisschain.matching.engine.outgoing.rabbit.impl.listeners

import com.rabbitmq.client.BuiltinExchangeType
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import com.swisschain.matching.engine.outgoing.rabbit.RabbitMqService
import com.swisschain.matching.engine.outgoing.rabbit.events.RabbitFailureEvent
import com.swisschain.matching.engine.outgoing.rabbit.events.RabbitReadyEvent
import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.matching.engine.utils.monitoring.HealthMonitorEvent
import com.swisschain.matching.engine.utils.monitoring.MonitoredComponent
import com.swisschain.utils.AppVersion
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingDeque
import javax.annotation.PostConstruct

@Component
class OrderBookListener {
    @Volatile
    private var failed = false

    @Autowired
    private lateinit var outgoingOrderBookQueue: BlockingDeque<OrderBookEvent>

    @Autowired
    private lateinit var rabbitMqService: RabbitMqService<Event>

    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @PostConstruct
    fun initRabbitMqPublisher() {
        rabbitMqService.startPublisher(config.me.rabbitMqConfigs.orderBooks,
                OrderBookListener::class.java.simpleName,
                outgoingOrderBookQueue,
                config.me.name,
                AppVersion.VERSION,
                BuiltinExchangeType.FANOUT)
    }

    @EventListener
    fun onFailure(rabbitFailureEvent: RabbitFailureEvent<*>) {
        if(rabbitFailureEvent.publisherName == OrderBookListener::class.java.simpleName) {
            failed = true
            logRmqFail(rabbitFailureEvent.publisherName)
            rabbitFailureEvent.failedEvent?.let {
                outgoingOrderBookQueue.putFirst(it as OrderBookEvent)
            }
            applicationEventPublisher.publishEvent(HealthMonitorEvent(false, MonitoredComponent.RABBIT, rabbitFailureEvent.publisherName))
        }
    }

    @EventListener
    fun onReady(rabbitReadyEvent: RabbitReadyEvent) {
        if (rabbitReadyEvent.publisherName == OrderBookListener::class.java.simpleName && failed) {
            failed = false
            logRmqRecover(rabbitReadyEvent.publisherName)
            applicationEventPublisher.publishEvent(HealthMonitorEvent(true, MonitoredComponent.RABBIT, rabbitReadyEvent.publisherName))
        }
    }
}