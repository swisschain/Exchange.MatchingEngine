package com.swisschain.matching.engine.keepalive

import com.swisschain.matching.engine.utils.monitoring.HealthMonitor
import com.swisschain.matching.engine.utils.monitoring.MonitoringStatsCollector
import com.swisschain.utils.AppVersion
import com.swisschain.utils.keepalive.http.IsAliveResponse
import com.swisschain.utils.keepalive.http.IsAliveResponseGetter
import org.apache.http.HttpStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


@Component("MeIsAliveResponseGetter")
class MeIsAliveResponseGetter @Autowired constructor (private val generalHealthMonitor: HealthMonitor,
                                                      private val monitoringStatsCollector: MonitoringStatsCollector): IsAliveResponseGetter() {

    override fun getResponse(): IsAliveResponse {
        val monitoringResult = monitoringStatsCollector.collectMonitoringResult()

        val ok = generalHealthMonitor.ok()
        val code: Int
        val message: String?
        if (ok) {
            code = HttpStatus.SC_OK
            message = null
        } else {
            code = HttpStatus.SC_INTERNAL_SERVER_ERROR
            message = "Internal Matching Engine error"
        }
        return MeIsAliveResponse(AppVersion.VERSION, code, monitoringResult, message)
    }
}