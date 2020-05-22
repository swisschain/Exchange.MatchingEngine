package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.wallet.Wallet

interface WalletDatabaseAccessor {
    fun loadWallets(): MutableMap<String, MutableMap<Long, Wallet>>
    fun insertOrUpdateWallet(wallet: Wallet) { insertOrUpdateWallets(listOf(wallet)) }
    fun insertOrUpdateWallets(wallets: List<Wallet>)
}