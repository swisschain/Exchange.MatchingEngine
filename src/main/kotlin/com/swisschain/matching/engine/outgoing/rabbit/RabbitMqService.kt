package com.swisschain.matching.engine.outgoing.rabbit

import com.rabbitmq.client.BuiltinExchangeType
import com.swisschain.matching.engine.logging.DatabaseLogger
import com.swisschain.matching.engine.utils.config.RabbitConfig
import java.util.concurrent.BlockingQueue

interface RabbitMqService<T> {
    fun startPublisher(config: RabbitConfig,
                       publisherName: String,
                       queue: BlockingQueue<out T>,
                       appName: String,
                       appVersion: String,
                       exchangeType: BuiltinExchangeType,
                       messageDatabaseLogger: DatabaseLogger<T>? = null)
}