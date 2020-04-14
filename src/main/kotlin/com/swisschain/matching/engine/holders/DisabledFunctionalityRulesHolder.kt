package com.swisschain.matching.engine.holders

import com.google.gson.Gson
import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.daos.DisabledFunctionalityData
import com.swisschain.matching.engine.daos.DisabledFunctionalityRule
import com.swisschain.matching.engine.daos.OperationType
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.cache.ApplicationGroupDeleteEvent
import com.swisschain.matching.engine.database.cache.ApplicationSettingCreateOrUpdateEvent
import com.swisschain.matching.engine.database.cache.ApplicationSettingDeleteEvent
import com.swisschain.matching.engine.database.cache.ApplicationSettingsCache
import com.swisschain.matching.engine.services.events.NewAssetPairsEvent
import com.swisschain.matching.engine.services.events.RemovedAssetPairsEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class DisabledFunctionalityRulesHolder(val applicationSettingsCache: ApplicationSettingsCache,
                                       val assetsPairsHolder: AssetsPairsHolder) {

    @Volatile
    private var disabledFunctionalityData = DisabledFunctionalityData()

    @Autowired
    private lateinit var gson: Gson

    fun isTradeDisabled(assetPair: AssetPair?): Boolean {
        val disabledFunctionalityDataToCheck = disabledFunctionalityData

        if (assetPair == null) {
            return disabledFunctionalityDataToCheck.disabledOperations.contains(OperationType.TRADE)
        }

        return disabledFunctionalityDataToCheck.disabledOperations.contains(OperationType.TRADE)
                || disabledFunctionalityDataToCheck.disabledAssetPairIds.contains(assetPair.assetPairId)
    }

    fun isCashInDisabled(asset: Asset?): Boolean {
        val disabledFunctionalityDataToCheck = disabledFunctionalityData

        if (asset == null) {
            return disabledFunctionalityDataToCheck.disabledOperations.contains(OperationType.CASH_IN)
        }

        return disabledFunctionalityDataToCheck.disabledOperations.contains(OperationType.CASH_IN)
                || (disabledFunctionalityDataToCheck.disabledOperationsByAsset[asset.assetId]?.contains(OperationType.CASH_IN)
                ?: false)
                || disabledFunctionalityDataToCheck.disabledAssetIds.contains(asset.assetId)

    }

    fun isCashOutDisabled(asset: Asset?): Boolean {
        val disabledFunctionalityDataToCheck = disabledFunctionalityData

        if (asset == null) {
            return disabledFunctionalityDataToCheck.disabledOperations.contains(OperationType.CASH_OUT)
        }

        return disabledFunctionalityDataToCheck.disabledOperations.contains(OperationType.CASH_OUT)
                || (disabledFunctionalityDataToCheck.disabledOperationsByAsset[asset.assetId]?.contains(OperationType.CASH_OUT)
                ?: false)
                || disabledFunctionalityDataToCheck.disabledAssetIds.contains(asset.assetId)
    }

    fun isCashTransferDisabled(asset: Asset?): Boolean {
        val disabledFunctionalityDataToCheck = disabledFunctionalityData

        if (asset == null) {
            return disabledFunctionalityDataToCheck.disabledOperations.contains(OperationType.CASH_TRANSFER)
        }

        return disabledFunctionalityDataToCheck.disabledOperations.contains(OperationType.CASH_TRANSFER)
                || (disabledFunctionalityDataToCheck.disabledOperationsByAsset[asset.assetId]?.contains(OperationType.CASH_TRANSFER)
                ?: false)
                || disabledFunctionalityDataToCheck.disabledAssetIds.contains(asset.assetId)
    }

    @PostConstruct
    private fun init() {
        val disabledAssetPairIds = HashSet<String>()
        val disabledOperations = HashSet<OperationType>()
        val disabledAssetIds = HashSet<String>()
        val disabledOperationsByAsset = HashMap<String, MutableSet<OperationType>>()

        applicationSettingsCache.getSettingsGroup(AvailableSettingGroup.DISABLED_FUNCTIONALITY_RULES, true)?.settings?.forEach {
            val disabledRule = gson.fromJson(it.value, DisabledFunctionalityRule::class.java)

            if (disabledRule.assetPairId != null) {
                disabledAssetPairIds.add(disabledRule.assetPairId)
            }

            if (disabledRule.assetId != null) {
                if (disabledRule.operationType == OperationType.TRADE) {
                    disabledAssetPairIds.addAll(assetsPairsHolder.getAssetPairsByAssetId(disabledRule.brokerId, disabledRule.assetId).map { it.assetPairId })
                    return@forEach
                }

                if (disabledRule.operationType == null) {
                    disabledAssetPairIds.addAll(assetsPairsHolder.getAssetPairsByAssetId(disabledRule.brokerId, disabledRule.assetId).map { it.assetPairId })
                    disabledAssetIds.add(disabledRule.assetId)
                    return@forEach
                } else {
                    val disabledOperationsForAsset = disabledOperationsByAsset.getOrPut(disabledRule.assetId) { HashSet() }
                    disabledOperationsForAsset.add(disabledRule.operationType)
                    return@forEach
                }
            }

            if (disabledRule.operationType != null && disabledRule.assetId == null && disabledRule.assetPairId == null) {
                disabledOperations.add(disabledRule.operationType)
            }
        }

        disabledFunctionalityData = DisabledFunctionalityData(disabledAssetPairIds = disabledAssetPairIds,
                disabledOperations = disabledOperations,
                disabledAssetIds = disabledAssetIds,
                disabledOperationsByAsset = disabledOperationsByAsset)

    }

    @EventListener
    private fun onSettingCreateOrUpdate(applicationSettingUpdateEvent: ApplicationSettingCreateOrUpdateEvent) {
        init()
    }

    @EventListener
    private fun onSettingRemove(applicationSettingDeleteEvent: ApplicationSettingDeleteEvent) {
        init()
    }

    @EventListener
    private fun onSettingGroupRemove(applicationGroupDeleteEvent: ApplicationGroupDeleteEvent) {
        init()
    }

    @EventListener
    private fun onNewAssetPair(newAssetPairsEvent: NewAssetPairsEvent) {
        init()
    }

    @EventListener
    private fun onSettingDelete(removedAssetPairsEvent: RemovedAssetPairsEvent) {
        init()
    }

    public fun update() {
        init()
    }

    private fun clear() {
        disabledFunctionalityData = DisabledFunctionalityData()
    }
}