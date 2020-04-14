package com.swisschain.matching.engine.holders

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.database.cache.AssetsCache
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.services.validators.impl.ValidationException.Validation.UNKNOWN_ASSET
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AssetsHolder @Autowired constructor (private val assetsCache: AssetsCache) {
    fun getAsset(brokerId: String, assetId: String): Asset {
        return getAssetAllowNulls(brokerId, assetId) ?: throw ValidationException(UNKNOWN_ASSET, "Unable to find asset $assetId")
    }

    fun getAssetAllowNulls(brokerId: String, assetId: String): Asset? {
        return assetsCache.getAsset(brokerId, assetId)
    }
}