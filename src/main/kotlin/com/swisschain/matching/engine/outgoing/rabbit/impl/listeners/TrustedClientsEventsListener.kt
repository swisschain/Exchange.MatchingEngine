package com.swisschain.matching.engine.outgoing.rabbit.impl.listeners

import com.rabbitmq.client.BuiltinExchangeType
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.rabbit.RabbitMqService
import com.swisschain.matching.engine.outgoing.rabbit.utils.RabbitEventUtils
import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.utils.AppVersion
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingQueue
import javax.annotation.PostConstruct

@Component
class TrustedClientsEventsListener {

    @Autowired
    private lateinit var rabbitMqService: RabbitMqService<Event>

    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @PostConstruct
    fun initRabbitMqPublisher() {
        config.me.rabbitMqConfigs.trustedClientsEvents.forEachIndexed { index, rabbitConfig ->
            val trustedClientsEventConsumerQueue = RabbitEventUtils.getTrustedClientsEventConsumerQueueName(rabbitConfig.exchange, index)
            val queue = applicationContext.getBean(trustedClientsEventConsumerQueue) as BlockingQueue<Event>
            rabbitMqService.startPublisher(rabbitConfig,
                    trustedClientsEventConsumerQueue,
                    queue,
                    config.me.name,
                    AppVersion.VERSION,
                    BuiltinExchangeType.FANOUT,
                    null)
        }
    }
}