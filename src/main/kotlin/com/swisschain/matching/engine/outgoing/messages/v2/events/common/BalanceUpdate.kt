package com.swisschain.matching.engine.outgoing.messages.v2.events.common

import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages

class BalanceUpdate(val brokerId: String,
                    val accountId: Long,
                    val walletId: Long,
                    val assetId: String,
                    val oldBalance: String,
                    val newBalance: String,
                    val oldReserved: String,
                    val newReserved: String) : EventPart<OutgoingMessages.BalanceUpdate.Builder> {

    override fun createGeneratedMessageBuilder(): OutgoingMessages.BalanceUpdate.Builder {
        val builder = OutgoingMessages.BalanceUpdate.newBuilder()
        builder.setBrokerId(brokerId)
                .setAccountId(accountId)
                .setWalletId(walletId)
                .setAssetId(assetId)
                .setOldBalance(oldBalance)
                .setNewBalance(newBalance)
                .setOldReserved(oldReserved)
                .newReserved = newReserved
        return builder
    }
}