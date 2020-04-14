package com.swisschain.utils.logging.config

import java.util.concurrent.TimeUnit

data class SlackNotificationConfig(
        val grpcErrorLogWriterConnection: String,
        val throttlingLimitSeconds: Int = 60,
        val messagesTtlMinutes: Int = 60,
        val cleanerInterval: Long = TimeUnit.HOURS.toMillis(3)
)