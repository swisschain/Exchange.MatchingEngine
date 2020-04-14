package com.swisschain.matching.engine.daos.setting

import com.swisschain.matching.engine.exception.MatchingEngineException

class SettingNotFoundException(val settingName: String): MatchingEngineException("Setting with name '$settingName' is not found")