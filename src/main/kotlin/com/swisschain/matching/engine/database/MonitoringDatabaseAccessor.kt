package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.TypePerformanceStats
import com.swisschain.matching.engine.daos.monitoring.MonitoringResult

interface MonitoringDatabaseAccessor {
    fun saveMonitoringResult(monitoringResult: MonitoringResult)
    fun savePerformanceStats(stats: TypePerformanceStats)
}