package com.swisschain.matching.engine.daos

import java.math.BigDecimal

class AssetPair(
        val brokerId: String,
        val assetPairId: String,
        val baseAssetId: String,
        val quotingAssetId: String,
        val accuracy: Int,
        val minVolume: BigDecimal,
        val maxVolume: BigDecimal,
        val maxValue: BigDecimal,
        val marketOrderPriceDeviationThreshold: BigDecimal
) {
    override fun toString(): String {
        return "AssetPair(" +
                "brokerId='$brokerId', " +
                "assetPairId='$assetPairId', " +
                "baseAssetId='$baseAssetId', " +
                "quotingAssetId='$quotingAssetId', " +
                "accuracy=$accuracy, " +
                "minVolume=$minVolume, " +
                "maxVolume=$maxVolume, " +
                "maxValue=$maxValue, " +
                "marketOrderPriceDeviationThreshold=$marketOrderPriceDeviationThreshold"
    }
}