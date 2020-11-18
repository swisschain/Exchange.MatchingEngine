package com.swisschain.matching.engine.database.stub

import com.swisschain.matching.engine.daos.setting.SettingHistoryRecord
import com.swisschain.matching.engine.database.SettingsHistoryDatabaseAccessor
import java.util.*

class StubSettingsHistoryDatabaseAccessor(): SettingsHistoryDatabaseAccessor {

    override fun save(settingHistoryRecord: SettingHistoryRecord) {
    }

    override fun get(settingGroupName: String, settingName: String): List<SettingHistoryRecord> {
        return LinkedList<SettingHistoryRecord>()
    }

    override fun getAll(settingGroupName: String): List<SettingHistoryRecord> {
        return LinkedList<SettingHistoryRecord>()
    }
}