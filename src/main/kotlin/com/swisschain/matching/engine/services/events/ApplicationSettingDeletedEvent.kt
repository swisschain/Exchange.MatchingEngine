package com.swisschain.matching.engine.services.events

import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.daos.setting.Setting

class ApplicationSettingDeletedEvent(val settingGroup: AvailableSettingGroup,
                                     val deletedSetting: Setting,
                                     val comment: String,
                                     val user: String)