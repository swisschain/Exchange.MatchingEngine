package com.swisschain.matching.engine.balance

import java.math.BigDecimal

interface BalancesGetter {
    fun getAvailableBalance(brokerId:String, walletId: String, assetId: String): BigDecimal
    fun getAvailableReservedBalance(brokerId:String, walletId: String, assetId: String): BigDecimal
    fun getReservedBalance(brokerId:String, walletId: String, assetId: String): BigDecimal
}