package com.swisschain.matching.engine.database.grpc

import com.swisschain.utils.alivestatus.database.AliveStatusDatabaseAccessor
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class GrpcAliveStatusDatabaseAccessor(
        private val grpcConnectionString: String,
        private val appName: String,
        private val ip: String): AliveStatusDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcAliveStatusDatabaseAccessor::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var grpcStub: GrpcAliveStatusServiceGrpc.GrpcAliveStatusServiceBlockingStub = GrpcAliveStatusServiceGrpc.newBlockingStub(channel)

    override fun checkAndLock(): Boolean {
        try {
            val response = grpcStub.checkAndLock(convertToCheckRequest(appName, ip))
            return if (response.success) {
                true
            } else {
                LOGGER.error("Unable to start: ${response.reason.value}")
                false
            }
        } catch (e: Exception) {
            LOGGER.error("Unable to checkAndLock", e)
            channel.shutdown()
            initConnection()
        }
        return false
    }

    override fun keepAlive() {
        try {
            grpcStub.keepAlive(convertToCheckRequest(appName, ip))
        } catch (e: Exception) {
            LOGGER.error("Unable to keepAlive", e)
            channel.shutdown()
            initConnection()
        }
    }

    override fun unlock() {
        try {
            grpcStub.unlock(convertToCheckRequest(appName, ip))
        } catch (e: Exception) {
            LOGGER.error("Unable to unlock", e)
            channel.shutdown()
            initConnection()
        }
    }

    private fun convertToCheckRequest(appName: String, ip: String): GrpcAliveStatus.CheckRequest {
        return GrpcAliveStatus.CheckRequest.newBuilder().setAppName(appName).setIp(ip).build()
    }

    @Synchronized
    private fun initConnection() {
        channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
        grpcStub = GrpcAliveStatusServiceGrpc.newBlockingStub(channel)
    }
}