package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.web.dto.DeleteSettingRequestDto
import com.swisschain.matching.engine.web.dto.SettingDto
import com.swisschain.matching.engine.web.dto.SettingsGroupDto

interface ApplicationSettingsService {
    fun getAllSettingGroups(enabled: Boolean? = null): Set<SettingsGroupDto>
    fun getSettingsGroup(settingsGroup: AvailableSettingGroup, enabled: Boolean? = null): SettingsGroupDto?
    fun getSetting(settingsGroup: AvailableSettingGroup, settingName: String): SettingDto?
    fun getHistoryRecords(settingsGroupName: String, settingName: String): List<SettingDto>
    fun getAllLastHistoryRecords(settingsGroupName: String): List<SettingDto>

    fun createOrUpdateSetting(settingsGroup: AvailableSettingGroup, settingDto: SettingDto)

    fun deleteSettingsGroup(settingsGroup: AvailableSettingGroup, deleteSettingRequestDto: DeleteSettingRequestDto)
    fun deleteSetting(settingsGroup: AvailableSettingGroup, settingName: String, deleteSettingRequestDto: DeleteSettingRequestDto)
}