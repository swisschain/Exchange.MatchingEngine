package com.swisschain.matching.engine.balance

import java.math.BigDecimal

interface BalancesGetter {
    fun getAvailableBalance(brokerId:String, accountId: Long, walletId: Long, assetId: String): BigDecimal
    fun getAvailableReservedBalance(brokerId:String, accountId: Long, walletId: Long, assetId: String): BigDecimal
    fun getReservedBalance(brokerId:String, accountId: Long, walletId: Long, assetId: String): BigDecimal
}