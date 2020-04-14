package com.swisschain.matching.engine.daos.monitoring

import java.util.Date

data class MonitoringResult(
        val timestamp: Date,
        val vmCpuLoad: Double,
        val totalCpuLoad: Double,
        val totalMemory: Long,
        val freeMemory: Long,
        val maxHeap: Long,
        val totalHeap: Long,
        val freeHeap: Long,
        val totalSwap: Long,
        val freeSwap: Long,
        val threadsCount: Int
)