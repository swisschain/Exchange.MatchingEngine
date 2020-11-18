package com.swisschain.matching.engine.database.stub

import com.swisschain.matching.engine.daos.TypePerformanceStats
import com.swisschain.matching.engine.daos.monitoring.MonitoringResult
import com.swisschain.matching.engine.database.MonitoringDatabaseAccessor
import com.swisschain.utils.logging.ThrottlingLogger

class StubMonitoringDatabaseAccessor(): MonitoringDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(StubMonitoringDatabaseAccessor::class.java.name)
    }

    override fun saveMonitoringResult(monitoringResult: MonitoringResult) {
        LOGGER.debug(monitoringResult.toString())
    }

    override fun savePerformanceStats(stats: TypePerformanceStats) {
        LOGGER.debug(stats.toString())
    }
}