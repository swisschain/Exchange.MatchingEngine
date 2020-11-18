package com.swisschain.matching.engine.database.stub

import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.daos.setting.Setting
import com.swisschain.matching.engine.daos.setting.SettingsGroup
import com.swisschain.matching.engine.database.SettingsDatabaseAccessor

class StubSettingsDatabaseAccessor(): SettingsDatabaseAccessor {
    override fun getSetting(settingGroup: AvailableSettingGroup, settingName: String, enabled: Boolean?): Setting? {
        return null
    }

    override fun getSettingsGroup(settingGroup: AvailableSettingGroup, enabled: Boolean?): SettingsGroup? {
        return null
    }

    override fun getAllSettingGroups(enabled: Boolean?): Set<SettingsGroup> {
        return HashSet<SettingsGroup>()
    }

    override fun createOrUpdateSetting(settingGroup: AvailableSettingGroup, setting: Setting) {
    }

    override fun deleteSetting(settingGroup: AvailableSettingGroup, settingName: String) {
    }

    override fun deleteSettingsGroup(settingGroup: AvailableSettingGroup) {
    }
}