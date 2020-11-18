package com.swisschain.matching.engine.utils.config

import com.google.gson.annotations.SerializedName

data class RabbitMqConfigs(
        @SerializedName("OrderBooks")
        val orderBooks: RabbitConfig,
        val events: Set<RabbitConfig>,
        val trustedClientsEvents: Set<RabbitConfig>,
        @SerializedName("HeartBeatTimeout")
        val heartBeatTimeout: Long,
        @SerializedName("HandshakeTimeout")
        val handshakeTimeout: Long
)