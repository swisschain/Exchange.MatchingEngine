package com.swisschain.matching.engine.database.grpc

import com.swisschain.matching.engine.daos.TypePerformanceStats
import com.swisschain.matching.engine.daos.monitoring.MonitoringResult
import com.swisschain.matching.engine.database.MonitoringDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class GrpcMonitoringDatabaseAccessor(private val grpcConnectionString: String): MonitoringDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcMonitoringDatabaseAccessor::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var grpcStub: GrpcMonitoringServiceGrpc.GrpcMonitoringServiceBlockingStub = GrpcMonitoringServiceGrpc.newBlockingStub(channel)

    override fun saveMonitoringResult(monitoringResult: MonitoringResult) {
        try {
            grpcStub.saveMonitoringStats(convertToMonitoringStats(monitoringResult))
        } catch (e: Exception) {
            LOGGER.error("Unable to save monitoring results $monitoringResult", e)
            channel.shutdown()
            initConnection()
        }
    }

    private fun convertToMonitoringStats(monitoringResult: MonitoringResult): GrpcMonitoring.MonitoringStats {
        with (monitoringResult) {
            return GrpcMonitoring.MonitoringStats.newBuilder().setTimestamp(timestamp.createProtobufTimestampBuilder()).setVmCpuLoad(vmCpuLoad)
                    .setTotalCpuLoad(totalCpuLoad).setTotalMemory(totalMemory).setFreeMemory(freeMemory)
                    .setMaxHeap(maxHeap).setTotalHeap(totalHeap).setFreeHeap(freeHeap).setTotalSwap(totalSwap)
                    .setFreeSwap(freeSwap).setThreadsCount(threadsCount).build()
        }
    }

    override fun savePerformanceStats(stats: TypePerformanceStats) {
        try {
            grpcStub.savePerformanceStats(convertToPerformanceStats(stats))
        } catch (e: Exception) {
            LOGGER.error("Unable to save performance stats $stats", e)
            channel.shutdown()
            initConnection()
        }
    }

    private fun convertToPerformanceStats(stats: TypePerformanceStats): GrpcMonitoring.PerformanceStats {
        with (stats) {
            return GrpcMonitoring.PerformanceStats.newBuilder().setTimestamp(timestamp.createProtobufTimestampBuilder()).setType(type)
                    .setInputQueueTime(inputQueueTime?:"").setPreProcessingTime(preProcessingTime?:"")
                    .setPreProcessedMessageQueueTime(preProcessedMessageQueueTime).setTotalTime(totalTime)
                    .setProcessingTime(processingTime).setCount(count).setPersistTime(persistTime)
                    .setWriteResponseTime(writeResponseTime).setPersistCount(persistCount)
                    .setAppVersion(appVersion).build()
        }
    }

    @Synchronized
    private fun initConnection() {
        channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
        grpcStub = GrpcMonitoringServiceGrpc.newBlockingStub(channel)
    }
}