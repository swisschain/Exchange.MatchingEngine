package com.swisschain.matching.engine.balance

import java.math.BigDecimal

interface BalancesGetter {
    fun getAvailableBalance(brokerId:String, walletId: Long, assetId: String): BigDecimal
    fun getAvailableReservedBalance(brokerId:String, walletId: Long, assetId: String): BigDecimal
    fun getReservedBalance(brokerId:String, walletId: Long, assetId: String): BigDecimal
}