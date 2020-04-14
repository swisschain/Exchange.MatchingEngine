package com.swisschain.matching.engine.database.cache

import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.daos.setting.Setting

class ApplicationSettingCreateOrUpdateEvent(val settingGroup: AvailableSettingGroup, val setting: Setting)