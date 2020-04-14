package com.swisschain.matching.engine.database.grpc

import com.swisschain.matching.engine.daos.balance.ReservedVolumeCorrection
import com.swisschain.matching.engine.database.ReservedVolumesDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class GrpcReservedVolumeCorrectionDatabaseAccessor(private val grpcConnectionString: String): ReservedVolumesDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcReservedVolumeCorrectionDatabaseAccessor::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var grpcStub: GrpcReservedVolumeCorrectionServiceGrpc.GrpcReservedVolumeCorrectionServiceBlockingStub = GrpcReservedVolumeCorrectionServiceGrpc.newBlockingStub(channel)

    override fun addCorrectionsInfo(corrections: List<ReservedVolumeCorrection>) {
        try {
            grpcStub.saveReservedVolumeCorrection(convertToReservedVolumeCsorrections(corrections))
        } catch (e: Exception) {
            LOGGER.error("Unable to add correction infos $corrections", e)
            channel.shutdown()
            initConnection()
        }
    }

    private fun convertToReservedVolumeCsorrections(corrections: List<ReservedVolumeCorrection>): GrpcReservedVolumeCorrection.ReservedVolumeCorrectionRequest {
        val builder = GrpcReservedVolumeCorrection.ReservedVolumeCorrectionRequest.newBuilder()
        corrections.forEach { correction ->
            with (correction) {
                builder.addInfos(GrpcReservedVolumeCorrection.ReservedVolumeCorrection.newBuilder().setTimestamp(correction.timestamp.createProtobufTimestampBuilder())
                        .setWalletId(walletId).setAssetId(assetId).setOrderIds(orderIds)
                        .setOldReserved(oldReserved.toString()).setNewReserved(newReserved.toString()).build())
            }
        }

        return builder.build()
    }

    @Synchronized
    private fun initConnection() {
        channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
        grpcStub = GrpcReservedVolumeCorrectionServiceGrpc.newBlockingStub(channel)
    }
}