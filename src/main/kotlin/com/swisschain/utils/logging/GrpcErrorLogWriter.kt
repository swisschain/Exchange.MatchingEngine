package com.swisschain.utils.logging

import com.swisschain.matching.engine.database.grpc.GrpcErrorsLog
import com.swisschain.matching.engine.database.grpc.GrpcErrorsLogServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class GrpcErrorLogWriter(private val grpcConnectionString: String) {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcErrorLogWriter::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var grpcStub = GrpcErrorsLogServiceGrpc.newBlockingStub(channel)

    internal fun log(error: Error) {
        try {
            with (error) {
                grpcStub.log(GrpcErrorsLog.ErrorsLog.newBuilder().setType(type).setSender(sender).setMessage(message).build())
            }
        } catch (e: Exception) {
            LOGGER.error("Unable to log error $error : ${e.message}", e)
            channel.shutdown()
            channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
            grpcStub = GrpcErrorsLogServiceGrpc.newBlockingStub(channel)
        }
    }
}