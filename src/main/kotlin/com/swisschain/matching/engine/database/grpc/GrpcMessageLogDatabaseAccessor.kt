package com.swisschain.matching.engine.database.grpc

import com.swisschain.matching.engine.daos.Message
import com.swisschain.matching.engine.database.MessageLogDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class GrpcMessageLogDatabaseAccessor(private val grpcConnectionString: String): MessageLogDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcMessageLogDatabaseAccessor::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var grpcStub: GrpcMessageLogServiceGrpc.GrpcMessageLogServiceBlockingStub = GrpcMessageLogServiceGrpc.newBlockingStub(channel)

    override fun log(message: Message) {
        try {
            grpcStub.saveMessage(convertToMessage(message))
        } catch (e: Exception) {
            LOGGER.error("Unable to log $message", e)
            channel.shutdown()
            initConnection()
        }
    }

    private fun convertToMessage(message: Message): GrpcMessageLog.Message {
        with (message) {
            return GrpcMessageLog.Message.newBuilder().setSequenceNumber(sequenceNumber!!).setMessageId(messageId)
                    .setRequestId(requestId).setTimestamp(timestamp.createProtobufTimestampBuilder()).setMessage(message.message).build()
        }
    }

    @Synchronized
    private fun initConnection() {
        channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
        grpcStub = GrpcMessageLogServiceGrpc.newBlockingStub(channel)
    }
}