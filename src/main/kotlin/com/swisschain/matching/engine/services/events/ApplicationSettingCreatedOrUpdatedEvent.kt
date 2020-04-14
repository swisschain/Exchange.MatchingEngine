package com.swisschain.matching.engine.services.events

import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.daos.setting.Setting

class ApplicationSettingCreatedOrUpdatedEvent(val settingGroup: AvailableSettingGroup,
                                              val setting: Setting,
                                              val previousSetting: Setting?,
                                              val comment: String,
                                              val user: String)