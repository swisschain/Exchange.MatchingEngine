package com.swisschain.matching.engine.daos.monitoring

import java.util.*

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
) {
    override fun toString(): String {
        return "MonitoringResult(timestamp=$timestamp, vmCpuLoad=$vmCpuLoad, totalCpuLoad=$totalCpuLoad, totalMemory=$totalMemory, freeMemory=$freeMemory, maxHeap=$maxHeap, totalHeap=$totalHeap, freeHeap=$freeHeap, totalSwap=$totalSwap, freeSwap=$freeSwap, threadsCount=$threadsCount)"
    }
}