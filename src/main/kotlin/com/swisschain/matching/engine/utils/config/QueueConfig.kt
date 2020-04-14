package com.swisschain.matching.engine.utils.config

data class QueueConfig(val queueSizeHealthCheckInterval: Long,
                       val queueSizeLoggerInterval: Long,
                       val queueSizeLimit: Int,
                       val maxQueueSizeLimit: Int,
                       val recoverQueueSizeLimit: Int,
                       val outgoingMaxQueueSizeLimit: Int,
                       val outgoingRecoverQueueSizeLimit: Int,
                       val dataMaxQueueSizeLimit: Int,
                       val dataRecoverQueueSizeLimit: Int,
                       val orderBookMaxTotalSize: Int?)