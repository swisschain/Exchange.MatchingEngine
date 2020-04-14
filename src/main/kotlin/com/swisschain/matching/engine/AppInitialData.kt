package com.swisschain.matching.engine

data class AppInitialData(
        val ordersCount: Int,
        val stopOrdersCount: Int,
        val balancesCount: Int,
        val clientsCount: Int
)