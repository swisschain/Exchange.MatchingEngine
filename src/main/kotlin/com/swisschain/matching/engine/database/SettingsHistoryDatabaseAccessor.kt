package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.setting.SettingHistoryRecord

interface SettingsHistoryDatabaseAccessor {
    fun save(settingHistoryRecord: SettingHistoryRecord)
    fun get(settingGroupName: String, settingName: String): List<SettingHistoryRecord>
    fun getAll(settingGroupName: String): List<SettingHistoryRecord>
}