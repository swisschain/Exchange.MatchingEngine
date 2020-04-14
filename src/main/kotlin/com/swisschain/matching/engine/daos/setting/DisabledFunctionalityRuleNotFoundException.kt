package com.swisschain.matching.engine.daos.setting

import com.swisschain.matching.engine.exception.MatchingEngineException

class DisabledFunctionalityRuleNotFoundException(ruleId: String): MatchingEngineException("Disabled functionality rule with id: '$ruleId' is not found")
