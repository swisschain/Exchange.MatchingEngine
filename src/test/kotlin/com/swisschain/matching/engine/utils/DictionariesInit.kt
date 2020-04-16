package com.swisschain.matching.engine.utils

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.AssetPair
import java.math.BigDecimal

class DictionariesInit {
    companion object {
        fun createAsset(id: String, accuracy: Int, brokerId: String = DEFAULT_BROKER) = Asset(brokerId, id, accuracy)

        fun createAssetPair(
                assetPairId: String,
                baseAssetId: String,
                quotingAssetId: String,
                accuracy: Int,
                minVolume: BigDecimal = BigDecimal.ZERO,
                maxVolume: BigDecimal = BigDecimal(100000000),
                maxValue: BigDecimal = BigDecimal(100000000),
                marketOrderPriceDeviationThreshold: BigDecimal = -BigDecimal.ONE
        ) = AssetPair(DEFAULT_BROKER, assetPairId, baseAssetId, quotingAssetId, accuracy, minVolume, maxVolume, maxValue, marketOrderPriceDeviationThreshold)
    }
}