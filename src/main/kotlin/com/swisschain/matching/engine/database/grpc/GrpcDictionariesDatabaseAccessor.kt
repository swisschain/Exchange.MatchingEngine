package com.swisschain.matching.engine.database.grpc

import com.google.protobuf.Empty
import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.HashMap

class GrpcDictionariesDatabaseAccessor(
        private val defaultBrokerId: String,
        private val grpcConnectionString: String): DictionariesDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcDictionariesDatabaseAccessor::class.java.name)
    }

    private var channel: ManagedChannel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
    private var assetGrpcStub = AssetsGrpc.newBlockingStub(channel)
    private var assetPairGrpcStub = AssetPairsGrpc.newBlockingStub(channel)

    override fun loadAssets(): MutableMap<String, MutableMap<String, Asset>> {
        val result = HashMap<String, MutableMap<String, Asset>>()
        try {
            val response = assetGrpcStub.getAll(Empty.getDefaultInstance())
            response.assetsList.forEach { asset ->
                val brokerId = if (asset.brokerId.isNullOrEmpty()) defaultBrokerId else asset.brokerId
                result.getOrPut(brokerId) { HashMap<String, Asset>() } [asset.id] = convertToAsset(asset)
            }
        } catch (e: Exception) {
            LOGGER.error("Unable to load assets: ${e.message}", e)
            channel.shutdown()
            initConnection()
        }
        return result
    }

    override fun loadAsset(brokerId: String, assetId: String): Asset? {
        try {
            val response = assetGrpcStub.getById(
                    GrpcDictionaries.GetAssetByIdRequest.newBuilder().setBrokerId(brokerId).setAssetId(assetId).build())
            return if (response != null) {
                convertToAsset(response.asset)
            } else {
                null
            }
        } catch (e: Exception) {
            LOGGER.error("Unable to load asset $assetId: ${e.message}", e)
            channel.shutdown()
            initConnection()
        }
        return null
    }

    private fun convertToAsset(asset: GrpcDictionaries.Asset): Asset {
        return Asset(if (!asset.brokerId.isNullOrEmpty()) asset.brokerId else defaultBrokerId, asset.id, asset.name, asset.accuracy)
    }

    override fun loadAssetPairs(): Map<String, Map<String, AssetPair>> {
        val result = HashMap<String, MutableMap<String, AssetPair>>()
        try {
            val response = assetPairGrpcStub.getAll(Empty.getDefaultInstance())
            response.assetPairsList.forEach { assetPair ->
                val brokerId = if (assetPair.brokerId.isNullOrEmpty()) defaultBrokerId else assetPair.brokerId
                result.getOrPut(brokerId) { HashMap<String, AssetPair>() } [assetPair.id] = convertToAssetPair(assetPair)
            }
        } catch (e: Exception) {
            LOGGER.error("Unable to load asset pairs: ${e.message}", e)
            channel.shutdown()
            initConnection()
        }
        return result
    }

    override fun loadAssetPair(brokerId: String, assetPairId: String): AssetPair? {
        try {
            val response = assetPairGrpcStub.getById(
                    GrpcDictionaries.GetAssetPairByIdRequest.newBuilder().setBrokerId(brokerId).setAssetPairId(assetPairId).build())
            return if (response != null) {
                convertToAssetPair(response.assetPair)
            } else {
                null
            }
        } catch (e: Exception) {
            LOGGER.error("Unable to load asset pair $assetPairId: ${e.message}", e)
            channel.shutdown()
            initConnection()
        }
        return null
    }

    private fun convertToAssetPair(assetPair: GrpcDictionaries.AssetPair): AssetPair {
        return AssetPair(if (!assetPair.brokerId.isNullOrEmpty()) assetPair.brokerId else defaultBrokerId,
                assetPair.id, assetPair.baseAssetId, assetPair.quotingAssetId, assetPair.accuracy,
                assetPair.minVolume.toBigDecimal(), assetPair.maxVolume.toBigDecimal(), assetPair.maxOppositeVolume.toBigDecimal(),
                assetPair.marketOrderPriceThreshold.toBigDecimal())
    }

    @Synchronized
    private fun initConnection() {
        channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
        assetGrpcStub = AssetsGrpc.newBlockingStub(channel)
        assetPairGrpcStub = AssetPairsGrpc.newBlockingStub(channel)
    }
}