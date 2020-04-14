package com.swisschain.matching.engine.services.validators.settings

import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.web.dto.SettingDto

interface SettingValidator {
    fun getSettingGroup(): AvailableSettingGroup
    fun validate(setting: SettingDto)
}