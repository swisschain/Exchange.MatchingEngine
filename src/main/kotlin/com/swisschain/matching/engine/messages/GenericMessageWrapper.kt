package com.swisschain.matching.engine.messages

import com.google.protobuf.MessageOrBuilder
import com.google.protobuf.StringValue
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import io.grpc.stub.StreamObserver
import java.io.IOException

class GenericMessageWrapper(
        type: Byte,
        parsedMessage: MessageOrBuilder?,
        val callback: StreamObserver<IncomingMessages.Response>?,
        private val closeStream: Boolean = false,
        messageId: String? = null,
        id: String? = null,
        context: Any? = null
): MessageWrapper(type, parsedMessage, messageId, id, context) {

    override fun writeResponse(responseBuilder: MessageOrBuilder) {
        if (responseBuilder is IncomingMessages.Response.Builder) {
            if (!responseBuilder.hasMessageId() && messageId != null) {
                responseBuilder.messageId = StringValue.of(messageId)
            }

            if (!responseBuilder.hasId() && id != null) {
                responseBuilder.id = StringValue.of(id)
            }

            writeClientResponse(responseBuilder.build())
        } else {
            LOGGER.error("Unable to write for message with id $messageId response: Not valid type")
            METRICS_LOGGER.logError( "Unable to write response")
        }
    }

    private fun writeClientResponse(message: IncomingMessages.Response) {
        if (callback != null) {
            try {
                if (writeResponseTime != null) {
                    val errorMessage = "Can not write response - response was already written to gRPC, message id $messageId"
                    LOGGER.error(errorMessage)
                    METRICS_LOGGER.logError(errorMessage)
                    throw IllegalStateException(errorMessage)
                }
                val start = System.nanoTime()
                callback.onNext(message)
                writeResponseTime = System.nanoTime() - start
                if (closeStream) {
                    callback.onCompleted()
                }
            } catch (exception: IOException){
                LOGGER.error("Unable to write for message with id $messageId response: ${exception.message}", exception)
                METRICS_LOGGER.logError( "Unable to write response", exception)
            }
        }
    }
}