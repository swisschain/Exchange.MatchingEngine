package com.swisschain.matching.engine.outgoing.rabbit.impl.services

import com.google.gson.Gson
import com.rabbitmq.client.BuiltinExchangeType
import com.swisschain.matching.engine.logging.DatabaseLogger
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.rabbit.RabbitMqService
import com.swisschain.matching.engine.outgoing.rabbit.impl.publishers.RabbitMqPublisher
import com.swisschain.matching.engine.utils.config.RabbitConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.BlockingQueue

@Service("rabbitMqService")
@Profile("default")
class RabbitMqServiceImpl(private val gson: Gson,
                          @Value("#{Config.me.rabbitMqConfigs.heartBeatTimeout}")
                          private val heartBeatTimeout: Long,
                          @Value("#{Config.me.rabbitMqConfigs.handshakeTimeout}")
                          private val handshakeTimeout: Long,
                          private val applicationEventPublisher: ApplicationEventPublisher,
                          @Qualifier("outgoingPublishersThreadPool")
                          private val rabbitPublishersThreadPool: TaskExecutor) : RabbitMqService<Event> {
    override fun startPublisher(config: RabbitConfig, publisherName: String,
                                queue: BlockingQueue<out Event>, appName: String,
                                appVersion: String, exchangeType: BuiltinExchangeType,
                                messageDatabaseLogger: DatabaseLogger<Event>?) {
        rabbitPublishersThreadPool.execute(RabbitMqPublisher(config.uri, config.exchange, publisherName, queue, appName, appVersion, exchangeType,
                gson, applicationEventPublisher, heartBeatTimeout, handshakeTimeout, messageDatabaseLogger))
    }
}