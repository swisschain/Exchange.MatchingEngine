package com.swisschain.matching.engine.database.cache

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.concurrent.fixedRateTimer

@Component
class AssetsCache @Autowired constructor(
        private val databaseAccessor: DictionariesDatabaseAccessor,
        @Value("\${application.assets.cache.update.interval}") updateInterval: Long? = null) : DataCache() {

    companion object {
        val LOGGER = LoggerFactory.getLogger(AssetsCache::class.java)
    }

    private var assetsMap: Map<String, Map<String, Asset>> = HashMap()

    fun getAsset(brokerId: String, assetId: String): Asset? {
        return assetsMap[brokerId]?.get(assetId) ?: databaseAccessor.loadAsset(brokerId, assetId)
    }

    override fun update() {
        val newMap = databaseAccessor.loadAssets()
        if (newMap.isNotEmpty()) {
            assetsMap = newMap
        }
    }

    init {
        this.assetsMap = databaseAccessor.loadAssets()
        LOGGER.info("Loaded ${assetsMap.values.sumBy { it.size }} assets")
        updateInterval?.let {
            fixedRateTimer(name = "Assets Cache Updater", initialDelay = it, period = it) {
                update()
            }
        }
    }
}