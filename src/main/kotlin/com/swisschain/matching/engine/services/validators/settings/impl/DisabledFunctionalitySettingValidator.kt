package com.swisschain.matching.engine.services.validators.settings.impl

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.swisschain.matching.engine.daos.DisabledFunctionalityRule
import com.swisschain.matching.engine.daos.OperationType
import com.swisschain.matching.engine.daos.converters.DisabledFunctionalityRulesConverter.Companion.toDisabledFunctionalityRuleDto
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.services.validators.settings.SettingValidator
import com.swisschain.matching.engine.web.dto.DisabledFunctionalityRuleDto
import com.swisschain.matching.engine.web.dto.SettingDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DisabledFunctionalitySettingValidator(val assetsHolder: AssetsHolder,
                                            val assetsPairsHolder: AssetsPairsHolder) : SettingValidator {

    @Autowired
    private lateinit var gson: Gson

    override fun getSettingGroup(): AvailableSettingGroup {
        return AvailableSettingGroup.DISABLED_FUNCTIONALITY_RULES
    }

    fun validate(rule: DisabledFunctionalityRuleDto) {
        validateRuleIsNotEmpty(rule)
        validateOperation(rule)
        validateAssetExist(rule)
        validateAssetPairIdExist(rule)
    }

    override fun validate(setting: SettingDto) {
        try {
            val rule = gson.fromJson(setting.value, DisabledFunctionalityRule::class.java)
            validate(toDisabledFunctionalityRuleDto(rule))
        } catch (e: JsonSyntaxException) {
            throw ValidationException(validationMessage = "Invalid json was supplied: ${e.message}")
        }
    }

    private fun validateOperation(rule: DisabledFunctionalityRuleDto) {
        try {
            rule.operationType?.let {
                OperationType.valueOf(it)
            }
        } catch (e: IllegalArgumentException) {
            throw ValidationException(validationMessage = "Operation does not exist")
        }

        if (rule.assetPairId != null && rule.operationType != OperationType.TRADE.name) {
            throw ValidationException(validationMessage = "Rule with asset pair can have only ${OperationType.TRADE.name} operation")
        }
    }

    private fun validateAssetExist(rule: DisabledFunctionalityRuleDto) {
        if (isEmpty(rule.assetId)) {
            return
        }

        if (assetsHolder.getAssetAllowNulls(rule.brokerId, rule.assetId!!) == null) {
            throw ValidationException(validationMessage = "Asset does not exist")
        }
    }

    private fun validateAssetPairIdExist(rule: DisabledFunctionalityRuleDto) {
        if (isEmpty(rule.assetPairId)) {
            return
        }

        if (assetsPairsHolder.getAssetPairAllowNulls(rule.brokerId, rule.assetPairId!!) == null) {
            throw ValidationException(validationMessage = "Asset pair does not exist")
        }
    }

    private fun validateRuleIsNotEmpty(rule: DisabledFunctionalityRuleDto) {
        if (isEmpty(rule.assetId) &&
                isEmpty(rule.assetPairId) &&
                rule.operationType == null) {
            throw ValidationException(validationMessage = "All values of disabled functionality rule can not be empty")
        }
    }

    private fun isEmpty(string: String?): Boolean {
        return string == null || string.isEmpty()
    }
}