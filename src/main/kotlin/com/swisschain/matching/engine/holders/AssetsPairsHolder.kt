package com.swisschain.matching.engine.holders

import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.database.cache.AssetPairsCache
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AssetsPairsHolder @Autowired constructor(private val assetPairsCache: AssetPairsCache) {
    fun getAssetPairAllowNulls(brokerId: String, assetPairId: String): AssetPair? {
        return assetPairsCache.getAssetPair(brokerId, assetPairId)
    }

    fun getAssetPair(brokerId: String, assetPairId: String): AssetPair {
        return getAssetPairAllowNulls(brokerId, assetPairId) ?: throw Exception("Unable to find broker: $brokerId, asset pair $assetPairId")
    }

    fun getAssetPair(brokerId: String, assetId1: String, assetId2: String): AssetPair {
        return assetPairsCache.getAssetPair(brokerId, assetId1, assetId2) ?: throw Exception("Unable to find broker $brokerId asset pair for ($assetId1 & $assetId2)")
    }

    fun getAssetPairsByAssetId(brokerId: String, assetId: String): Set<AssetPair> {
        return assetPairsCache.getAssetPairByAssetId(brokerId, assetId)
    }
}