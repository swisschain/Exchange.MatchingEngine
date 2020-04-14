package com.swisschain.matching.engine.utils.monitoring

interface HealthMonitor {
    fun ok(): Boolean
}