package com.swisschain.matching.engine.order.transaction

import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.daos.wallet.Wallet
import com.swisschain.matching.engine.database.common.entity.BalancesData
import com.swisschain.matching.engine.holders.BalancesHolder
import java.math.BigDecimal

class CurrentTransactionBalancesHolder(private val balancesHolder: BalancesHolder) {

    private val changedBalancesByWalletIdAndAssetId = mutableMapOf<String, MutableMap<String, AssetBalance>>()
    private val changedWalletsByWalletId = mutableMapOf<String, Wallet>()

    fun updateBalance(brokerId: String, accountId: Long, walletId: Long, assetId: String, balance: BigDecimal) {
        val walletAssetBalance = getWalletAssetBalance(brokerId, accountId, walletId, assetId)
        walletAssetBalance.assetBalance.balance = balance
    }

    fun updateReservedBalance(brokerId: String, accountId: Long, walletId: Long, assetId: String, balance: BigDecimal) {
        val walletAssetBalance = getWalletAssetBalance(brokerId, accountId, walletId, assetId)
        walletAssetBalance.assetBalance.reserved = balance
    }

    fun persistenceData(): BalancesData {
        return BalancesData(changedWalletsByWalletId.values, changedBalancesByWalletIdAndAssetId.flatMap { it.value.values })
    }

    fun apply() {
        balancesHolder.setWallets(changedWalletsByWalletId.values)
    }

    fun getWalletAssetBalance(brokerId: String, accountId: Long, walletId: Long, assetId: String): WalletAssetBalance {
        val wallet = changedWalletsByWalletId.getOrPut("$brokerId-$walletId") {
            copyWallet(balancesHolder.wallets[brokerId]?.get(walletId)) ?: Wallet(brokerId, accountId, walletId)
        }
        val assetBalance = changedBalancesByWalletIdAndAssetId
                .getOrPut("$brokerId-$walletId") {
                    mutableMapOf()
                }
                .getOrPut(assetId) {
                    wallet.balances.getOrPut(assetId) { AssetBalance(brokerId, accountId, walletId, assetId) }
                }
        return WalletAssetBalance(wallet, assetBalance)
    }

    fun getChangedCopyOrOriginalAssetBalance(brokerId:String, accountId: Long, walletId: Long, assetId: String): AssetBalance {
        return (changedWalletsByWalletId["$brokerId-$walletId"] ?: balancesHolder.wallets[brokerId]?.get(walletId) ?: Wallet(brokerId, accountId, walletId)).balances[assetId]
                ?: AssetBalance(brokerId, accountId, walletId, assetId)
    }

    private fun copyWallet(wallet: Wallet?): Wallet? {
        if (wallet == null) {
            return null
        }
        return Wallet(wallet.brokerId, wallet.accountId, wallet.walletId, wallet.balances.values.map { copyAssetBalance(it) })
    }

    private fun copyAssetBalance(assetBalance: AssetBalance): AssetBalance {
        return AssetBalance(assetBalance.brokerId,
                assetBalance.accountId,
                assetBalance.walletId,
                assetBalance.asset,
                assetBalance.balance,
                assetBalance.reserved)
    }
}

class WalletAssetBalance(val wallet: Wallet, val assetBalance: AssetBalance)