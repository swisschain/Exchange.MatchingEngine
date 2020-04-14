package com.swisschain.matching.engine.database.grpc

import com.swisschain.matching.engine.daos.TransferOperation
import com.swisschain.matching.engine.database.CashOperationsDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

//TODO move persistence to post processing
class GrpcCashOperationsDatabaseAccessor(private val grpcConnectionString: String): CashOperationsDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcCashOperationsDatabaseAccessor::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var grpcStub:  GrpcCashOperationsServiceGrpc.GrpcCashOperationsServiceBlockingStub = GrpcCashOperationsServiceGrpc.newBlockingStub(channel)

    override fun insertTransferOperation(operation: TransferOperation) {
        try {
            grpcStub.saveTransferOperation(convertToTransferOperation(operation))
        } catch (e: Exception) {
            LOGGER.error("Unable to save transfer operation $operation", e)
            channel.shutdown()
            initConnection()
        }
    }

    private fun convertToTransferOperation(operation: TransferOperation): GrpcCashOperations.TransferOperation {
        with (operation) {
            return GrpcCashOperations.TransferOperation.newBuilder().setExternalId(externalId)
                    .setAssetId(asset.assetId).setFromWalletId(fromWalletId).setToWalletId(toWalletId)
                    .setTimestamp(dateTime.createProtobufTimestampBuilder()).setAmount(volume.toString()).build()
        }
    }

    @Synchronized
    private fun initConnection() {
        channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
        grpcStub = GrpcCashOperationsServiceGrpc.newBlockingStub(channel)
    }
}