package com.swisschain.matching.engine.balance.util

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.holders.BalancesHolder
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class TestBalanceHolderWrapper @Autowired constructor (private val balancesHolder: BalancesHolder)  {

    fun updateBalance(walletId: Long, assetId: String, balance: Double) {
        balancesHolder.updateBalance(null, null, DEFAULT_BROKER, 1000, walletId, assetId, BigDecimal.valueOf(balance))
    }

    fun updateReservedBalance(walletId: Long, assetId: String, reservedBalance: Double, skip: Boolean = false) {
        balancesHolder.updateReservedBalance(null, null, DEFAULT_BROKER, 1000, walletId, assetId, BigDecimal.valueOf(reservedBalance), skip)
    }
}