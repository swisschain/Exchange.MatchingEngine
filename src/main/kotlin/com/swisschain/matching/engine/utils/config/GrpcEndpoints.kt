package com.swisschain.matching.engine.utils.config

data class GrpcEndpoints(
        val cashApiServicePort: Int,
        val tradingApiServicePort: Int,
        val orderBooksServicePort: Int,
        val balancesServicePort: Int,

        val messageLogServiceConnection: String,
        val cashOperationsConnection: String,
        val reservedVolumesConnection: String,
        val settingsConnection: String,
        val settingsHistoryConnection: String,
        val monitoringConnection: String,
        val dictionariesConnection: String,
        val aliveStatusConnection: String,
        val outgoingEventsConnections: Set<String>,
        val outgoingTrustedClientsEventsConnections: Set<String>,
        val orderBooksConnection: String
)