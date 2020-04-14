package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.AssetPair
import java.util.HashMap

class TestDictionariesDatabaseAccessor : DictionariesDatabaseAccessor {

    private val assetPairs = HashMap<String, MutableMap<String, AssetPair>>()

    override fun loadAssetPairs(): Map<String, Map<String, AssetPair>> {
        return assetPairs
    }

    override fun loadAssetPair(brokerId: String, assetId: String): AssetPair? {
        return assetPairs[brokerId]?.get(assetId)
    }

    fun addAssetPair(pair: AssetPair) {
        assetPairs.getOrPut(pair.brokerId) { HashMap<String, AssetPair>() }[pair.assetPairId] = pair
    }

    fun clear() {
        assets.clear()
        assetPairs.clear()
    }

    val assets = HashMap<String, MutableMap<String, Asset>>()

    fun addAsset(asset: Asset) {
        assets.getOrPut(asset.brokerId) { HashMap<String, Asset>() }[asset.assetId] = asset
    }

    override fun loadAsset(brokerId: String, assetId: String): Asset? {
        return assets[brokerId]?.get(assetId)
    }

    override fun loadAssets(): MutableMap<String, MutableMap<String, Asset>> {
        return HashMap()
    }
}