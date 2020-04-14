package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.AssetPair

interface DictionariesDatabaseAccessor {
    fun loadAssets(): MutableMap<String, MutableMap<String, Asset>>
    fun loadAsset(brokerId: String, assetId: String): Asset?

    fun loadAssetPairs(): Map<String, Map<String, AssetPair>>
    fun loadAssetPair(brokerId: String, assetId: String): AssetPair?
}