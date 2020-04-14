package com.swisschain.matching.engine.utils.config

import com.google.gson.annotations.SerializedName
import com.swisschain.utils.logging.config.SlackNotificationConfig
import com.swisschain.utils.logging.config.ThrottlingLoggerConfig

data class Config(
    @SerializedName("MatchingEngine")
    val me: MatchingEngineConfig,
    val slackNotifications: SlackNotificationConfig,
    val throttlingLogger: ThrottlingLoggerConfig
)