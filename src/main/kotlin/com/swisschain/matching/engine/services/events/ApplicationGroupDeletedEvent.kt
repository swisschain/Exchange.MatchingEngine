package com.swisschain.matching.engine.services.events

import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.daos.setting.Setting

class ApplicationGroupDeletedEvent (val settingGroup: AvailableSettingGroup,
                                    val deletedSettings: Set<Setting>,
                                    val comment: String,
                                    val user: String)