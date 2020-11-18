package com.swisschain.matching.engine.database.stub

import com.swisschain.matching.engine.daos.Message
import com.swisschain.matching.engine.database.MessageLogDatabaseAccessor
import com.swisschain.matching.engine.database.grpc.GrpcMessageLog
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.utils.logging.ThrottlingLogger

class StubMessageLogDatabaseAccessor(): MessageLogDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(StubMessageLogDatabaseAccessor::class.java.name)
    }

    override fun log(message: Message) {
        LOGGER.debug(String.format("[%s] %s", message.messageId, message.message))
    }

    private fun convertToMessage(message: Message): GrpcMessageLog.Message {
        with (message) {
            return GrpcMessageLog.Message.newBuilder().setSequenceNumber(sequenceNumber!!).setMessageId(messageId)
                    .setRequestId(requestId).setTimestamp(timestamp.createProtobufTimestampBuilder()).setMessage(message.message).build()
        }
    }
}