package com.swisschain.matching.engine.keepalive

import com.swisschain.matching.engine.daos.monitoring.MonitoringResult
import com.swisschain.utils.keepalive.http.IsAliveResponse

class MeIsAliveResponse(version: String,
                        code: Int,
                        val monitoringResult: MonitoringResult?,
                        val errorMessage: String?): IsAliveResponse(version, code)