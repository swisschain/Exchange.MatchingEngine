package com.swisschain.matching.engine.daos.wallet

import java.math.BigDecimal
import java.util.HashMap

class Wallet {
    val brokerId: String
    val walletId: String
    val balances: MutableMap<String, AssetBalance> = HashMap()

    constructor(brokerId: String, walletId: String) {
        this.brokerId = brokerId
        this.walletId = walletId
    }

    constructor(brokerId: String, walletId: String, balances: List<AssetBalance>) {
        this.brokerId = brokerId
        this.walletId = walletId
        balances.forEach {
            this.balances[it.asset] = it
        }
    }

    fun setBalance(asset: String, balance: BigDecimal) {
        val oldBalance = balances[asset]
        if (oldBalance == null) {
            balances[asset] = AssetBalance(brokerId, walletId, asset, balance)
        } else {
            oldBalance.balance = balance
        }
    }

    fun setReservedBalance(asset: String, reservedBalance: BigDecimal) {
        val oldBalance = balances[asset]
        if (oldBalance == null) {
            balances[asset] = AssetBalance(brokerId, walletId, asset, reservedBalance, reservedBalance)
        } else {
            oldBalance.reserved = reservedBalance
        }
    }
}