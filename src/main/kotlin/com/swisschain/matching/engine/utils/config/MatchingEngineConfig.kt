package com.swisschain.matching.engine.utils.config

import com.swisschain.utils.alivestatus.config.AliveStatusConfig
import com.swisschain.utils.files.clean.config.LogFilesCleanerConfig
import com.swisschain.utils.keepalive.http.KeepAliveConfig

data class MatchingEngineConfig(
        val name: String,
        val redis: RedisConfig,
        val grpcEndpoints: GrpcEndpoints,
        val rabbitMqConfigs: RabbitMqConfigs,
        val httpOrderBookPort: Int,
        val httpApiPort: Int,
        val correctReservedVolumes: Boolean,
        val cancelMinVolumeOrders: Boolean,
        val cancelAllOrders: Boolean,
        val queueConfig: QueueConfig,
        val aliveStatus: AliveStatusConfig,
        val processedMessagesInterval: Long,
        val performanceStatsInterval: Long,
        val keepAlive: KeepAliveConfig,
        val logFilesCleaner: LogFilesCleanerConfig,
        val walletsMigration: Boolean,
        val writeBalancesToSecondaryDb: Boolean,
        val ordersMigration: Boolean,
        val writeOrdersToSecondaryDb: Boolean
)