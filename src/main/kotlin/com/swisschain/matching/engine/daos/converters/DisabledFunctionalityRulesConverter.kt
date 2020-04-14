package com.swisschain.matching.engine.daos.converters

import com.swisschain.matching.engine.daos.DisabledFunctionalityRule
import com.swisschain.matching.engine.daos.OperationType
import com.swisschain.matching.engine.web.dto.DisabledFunctionalityRuleDto
import java.util.Date

class DisabledFunctionalityRulesConverter {
    companion object {
        fun toDisabledFunctionalityRule(disabledFunctionalityRuleDto: DisabledFunctionalityRuleDto): DisabledFunctionalityRule {
            return disabledFunctionalityRuleDto.let { rule ->
                DisabledFunctionalityRule(rule.brokerId,
                        rule.assetId,
                        rule.assetPairId,
                        rule.operationType?.let { OperationType.valueOf(it) })
            }
        }

        fun toDisabledFunctionalityRuleDto(rule: DisabledFunctionalityRule,
                                           id: String?,
                                           timestamp: Date?,
                                           enabled: Boolean?,
                                           comment: String? = null,
                                           user: String? = null): DisabledFunctionalityRuleDto {
            return DisabledFunctionalityRuleDto(
                    id = id,
                    brokerId = rule.brokerId,
                    assetId = rule.assetId,
                    assetPairId = rule.assetPairId,
                    operationType = rule.operationType?.let { it.name },
                    enabled = enabled,
                    timestamp = timestamp,
                    comment = comment,
                    user = user)
        }

        fun toDisabledFunctionalityRuleDto(rule: DisabledFunctionalityRule): DisabledFunctionalityRuleDto {
            return toDisabledFunctionalityRuleDto(rule, null, null, null, null, null)
        }
    }
}