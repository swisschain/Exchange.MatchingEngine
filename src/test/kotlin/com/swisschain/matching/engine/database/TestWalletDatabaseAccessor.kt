package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.daos.wallet.Wallet
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.HashMap

@Component
class TestWalletDatabaseAccessor : WalletDatabaseAccessor {

    private val wallets = HashMap<String, MutableMap<String, Wallet>>()

    override fun loadWallets(): MutableMap<String, MutableMap<String, Wallet>> {
        return wallets
    }

    override fun insertOrUpdateWallets(wallets: List<Wallet>) {
        val walletIds = wallets.map { it.walletId }
        if (walletIds.size != walletIds.toSet().size) {
            throw Exception("Wallets list contains several wallets with the same client")
        }

        wallets.forEach { wallet ->
            val updatedWallet = this.wallets.getOrPut(wallet.brokerId) { HashMap() }.getOrPut(wallet.walletId) { Wallet(wallet.brokerId, wallet.walletId) }
            wallet.balances.values.forEach {
                updatedWallet.setBalance(it.asset, it.balance)
                updatedWallet.setReservedBalance(it.asset, it.reserved)
            }
        }
    }

    fun getBalance(walletId: String, assetId: String): BigDecimal {
        val client = wallets[DEFAULT_BROKER]?.get(walletId)?.balances
        if (client != null) {
            val wallet = client[assetId]
            if (wallet != null) {
                return wallet.balance
            }
        }
        return BigDecimal.ZERO
    }


    fun getReservedBalance(walletId: String, assetId: String): BigDecimal {
        val client = wallets[DEFAULT_BROKER]?.get(walletId)?.balances
        if (client != null) {
            val wallet = client[assetId]
            if (wallet != null) {
                return wallet.reserved
            }
        }
        return BigDecimal.ZERO
    }

    fun clear() {
        wallets.clear()
    }

}