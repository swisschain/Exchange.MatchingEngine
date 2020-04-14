package com.swisschain.matching.engine.incoming.preprocessor

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.ParsedData
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import java.util.concurrent.BlockingQueue

abstract class AbstractMessagePreprocessor<T : ParsedData>(private val contextParser: ContextParser<T>,
                                                           private val messageProcessingStatusHolder: MessageProcessingStatusHolder,
                                                           private val preProcessedMessageQueue: BlockingQueue<MessageWrapper>,
                                                           private val logger: ThrottlingLogger) : MessagePreprocessor {

    companion object {
        private val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    override fun preProcess(messageWrapper: MessageWrapper) {
        try {
            parseAndPreProcess(messageWrapper)
        } catch (e: Exception) {
            handlePreProcessingException(e, messageWrapper)
        }
    }

    private fun parseAndPreProcess(messageWrapper: MessageWrapper) {
        messageWrapper.messagePreProcessorStartTimestamp = System.nanoTime()
        val parsedData = parse(messageWrapper)
        val parsedMessageWrapper = parsedData.messageWrapper
        val preProcessSuccess = when {
            !messageProcessingStatusHolder.isMessageProcessingEnabled() -> {
                writeResponse(parsedMessageWrapper, MessageStatus.MESSAGE_PROCESSING_DISABLED, "Message processing is disabled")
                false
            }
            !messageProcessingStatusHolder.isHealthStatusOk() -> {
                val errorMessage = "Message processing is disabled"
                writeResponse(parsedMessageWrapper, MessageStatus.RUNTIME, errorMessage)
                logger.error( "Message processing is disabled, message type: ${MessageType.valueOf(messageWrapper.type)}, message id: ${messageWrapper.messageId}")
                METRICS_LOGGER.logError(errorMessage)
                false
            }
            else -> preProcessParsedData(parsedData)
        }
        parsedMessageWrapper.messagePreProcessorEndTimestamp = System.nanoTime()
        if (preProcessSuccess) {
            preProcessedMessageQueue.put(parsedMessageWrapper)
        }
    }

    protected abstract fun preProcessParsedData(parsedData: T): Boolean

    protected open fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus, message: String? = null) {
        val responseBuilder = IncomingMessages.Response.newBuilder().setStatus(IncomingMessages.Status.forNumber(status.type))
        message?.let { responseBuilder.setStatusReason(StringValue.of(it))}
        messageWrapper.writeResponse(responseBuilder)
    }

    private fun parse(messageWrapper: MessageWrapper): T {
        return contextParser.parse(messageWrapper)
    }

    private fun handlePreProcessingException(exception: Exception, message: MessageWrapper) {
        try {
            val context = message.context
            val errorMessage = "Got error during message preprocessing"
            logger.error("$errorMessage: ${exception.message} " +
                    if (context != null) "Error details: $context" else "", exception)
            METRICS_LOGGER.logError(errorMessage, exception)

            writeResponse(message, MessageStatus.RUNTIME, errorMessage)
        } catch (e: Exception) {
            val errorMessage = "Got error during message preprocessing failure handling"
            e.addSuppressed(exception)
            logger.error(errorMessage, e)
            METRICS_LOGGER.logError(errorMessage, e)
        }
    }
}