package com.swisschain.matching.engine.logging

import com.swisschain.matching.engine.database.MessageLogDatabaseAccessor
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import java.util.concurrent.BlockingQueue
import kotlin.concurrent.thread

class DatabaseLogger<in T>(private val dbAccessor: MessageLogDatabaseAccessor,
                           private val queue: BlockingQueue<MessageWrapper>) {

    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(DatabaseLogger::class.java.name)
        private val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    fun log(item: T) {
        queue.put(MessageWrapper(item as Any))
    }

    private fun takeAndSaveMessage() {
        try {
            val message = queue.take()
            dbAccessor.log(toLogMessage(message.item))
        } catch (e: Exception) {
            val errorMessage = "Unable to write log to DB: ${e.message}"
            LOGGER.error(errorMessage, e)
            METRICS_LOGGER.logError(errorMessage, e)
        }
    }

    init {
        thread(name = DatabaseLogger::class.java.name) {
            while (true) {
                takeAndSaveMessage()
            }
        }
    }
}

class MessageWrapper(val item: Any)