package com.swisschain.matching.engine.outgoing.rabbit.impl.publishers

import com.google.gson.Gson
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.swisschain.matching.engine.logging.DatabaseLogger
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.BlockingQueue

class RabbitMqPublisher(uri: String,
                        exchangeName: String,
                        publisherName: String,
                        queue: BlockingQueue<out Event>,
                        appName: String,
                        appVersion: String,
                        exchangeType: BuiltinExchangeType,
                        private val gson: Gson,
                        applicationEventPublisher: ApplicationEventPublisher,
                        heartBeatTimeout: Long,
                        handshakeTimeout: Long,
                        private val messageDatabaseLogger: DatabaseLogger<Event>? = null) : AbstractRabbitMqPublisher<Event>(uri, exchangeName, publisherName,
        queue, appName, appVersion, exchangeType, LOGGER,
        MESSAGES_LOGGER, METRICS_LOGGER, STATS_LOGGER, applicationEventPublisher, heartBeatTimeout, handshakeTimeout, messageDatabaseLogger) {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(RabbitMqPublisher::class.java.name)
        private val MESSAGES_LOGGER = LoggerFactory.getLogger("${RabbitMqPublisher::class.java.name}.message")
        private val METRICS_LOGGER = MetricsLogger.getLogger()
        private val STATS_LOGGER = LoggerFactory.getLogger("${RabbitMqPublisher::class.java.name}.stats")
    }

    override fun getRabbitPublishRequest(item: Event): RabbitPublishRequest {
        return RabbitPublishRequest(getRoutingKey(item), getBody(item), getLogMessage(item), getProps(item))

    }

    private fun getRoutingKey(item: Event): String {
        return item.header.messageType.id.toString()
    }

    private fun getBody(item: Event): ByteArray {
        return item.buildGeneratedMessage().toByteArray()
    }

    private fun getProps(item: Event): AMQP.BasicProperties {
        val headers = mapOf(Pair("MessageType", item.header.messageType.id),
                Pair("SequenceNumber", item.header.sequenceNumber),
                Pair("MessageId", item.header.messageId),
                Pair("RequestId", item.header.requestId),
                Pair("Version", item.header.version),
                Pair("Timestamp", item.header.timestamp.time),
                Pair("EventType", item.header.eventType))

        // MINIMAL_PERSISTENT_BASIC + headers
        return AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .headers(headers)
                .build()
    }

    private fun getLogMessage(item: Event): String? {
        return messageDatabaseLogger?.let { gson.toJson(item) }
    }
}