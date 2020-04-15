package com.swisschain.matching.engine.database.cache

import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.stream.Collectors
import kotlin.concurrent.fixedRateTimer

@Component
class AssetPairsCache @Autowired constructor(
        private val databaseAccessor: DictionariesDatabaseAccessor,
        @Value("\${application.assets.pair.cache.update.interval}") updateInterval: Long? = null) : DataCache() {

    private val knownAssetPairs = HashMap<String, MutableSet<String>>()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(AssetPairsCache::class.java)
    }

    @Volatile
    private var assetPairsById: Map<String, Map<String, AssetPair>> = HashMap()

    @Volatile
    private var assetPairsByPair: Map<String, Map<String, AssetPair>> = HashMap()

    init {
        this.assetPairsById = databaseAccessor.loadAssetPairs()
        this.assetPairsByPair = generateAssetPairsMapByPair(assetPairsById)
        assetPairsById.forEach {
            val brokerPairs = knownAssetPairs.getOrPut(it.key) { HashSet() }
            it.value.values.forEach{
                brokerPairs.add(it.symbol)
            }
        }
        LOGGER.info("Loaded ${assetPairsById.values.sumBy { it.size }} assets pairs")
        updateInterval?.let {
            fixedRateTimer(name = "Asset Pairs Cache Updater", initialDelay = it, period = it) {
                update()
            }
        }
    }

    fun getAssetPair(brokerId: String, assetPair: String): AssetPair? {
        return assetPairsById[brokerId]?.get(assetPair) ?: databaseAccessor.loadAssetPair(brokerId, assetPair)
    }

    fun getAssetPair(brokerId: String, assetId1: String, assetId2: String): AssetPair? {
        return assetPairsByPair[brokerId]?.get(pairKey(assetId1, assetId2)) ?: assetPairsByPair[brokerId]?.get(pairKey(assetId2, assetId1))
    }

    fun getAssetPairByAssetId(brokerId: String, assetId: String): Set<AssetPair> {
        return assetPairsById[brokerId]?.values
                ?.stream()
                ?.filter { it.quotingAssetId == assetId || it.baseAssetId == assetId }
                ?.collect(Collectors.toSet()) ?: HashSet()
    }

    override fun update() {
        val newMap = databaseAccessor.loadAssetPairs()
        if (newMap.isNotEmpty()) {
            val newMapByPair = generateAssetPairsMapByPair(newMap)
            assetPairsById = newMap
            assetPairsByPair = newMapByPair
        }
    }

    private fun generateAssetPairsMapByPair(assetPairsById: Map<String, Map<String, AssetPair>>): Map<String, Map<String, AssetPair>> {
        val result = HashMap<String, Map<String, AssetPair>>()
        assetPairsById.forEach { (brokerId, assetPairs) ->
            result[brokerId] = assetPairs.values
                    .groupBy { pairKey(it.baseAssetId, it.quotingAssetId) }
                    .mapValues {
                        if (it.value.size > 1) {
                            LOGGER.error("Asset pairs count for baseAssetId=${it.value.first().baseAssetId} and quotingAssetId=${it.value.first().quotingAssetId} is more than 1")
                        }
                        it.value.first()
                    }
        }
        return result
    }

    private fun pairKey(assetId1: String, assetId2: String) = "${assetId1}_$assetId2"
}