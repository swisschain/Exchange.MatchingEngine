package com.swisschain.matching.engine.messages

import com.google.protobuf.MessageOrBuilder
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger

abstract class MessageWrapper(
        val type: Byte,
        val parsedMessage: MessageOrBuilder?,
        var messageId: String? = null,
        var id: String? = null,
        var context: Any? = null,
        val startTimestamp: Long = System.nanoTime(),
        var timestamp: Long? = null,
        var triedToPersist: Boolean = false,
        var persisted: Boolean = false) {

    companion object {
        val LOGGER = ThrottlingLogger.getLogger(MessageWrapper::class.java.name)
        val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    var messagePreProcessorStartTimestamp: Long?  = null
    var messagePreProcessorEndTimestamp: Long? = null
    var writeResponseTime: Long? = null

    var processedMessage: ProcessedMessage? = null

    abstract fun writeResponse(responseBuilder: MessageOrBuilder)
}
