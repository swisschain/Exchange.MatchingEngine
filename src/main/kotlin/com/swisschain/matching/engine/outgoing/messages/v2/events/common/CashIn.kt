package com.swisschain.matching.engine.outgoing.messages.v2.events.common

import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages

class CashIn(val brokerId: String,
             val accountId: Long,
             val walletId: Long,
             val assetId: String,
             val volume: String,
             val description: String,
             val fees: List<Fee>?) : EventPart<OutgoingMessages.CashInEvent.CashIn.Builder> {

    override fun createGeneratedMessageBuilder(): OutgoingMessages.CashInEvent.CashIn.Builder {
        val builder = OutgoingMessages.CashInEvent.CashIn.newBuilder()
        builder.setBrokerId(brokerId)
                .setAccountId(accountId)
                .setWalletId(walletId)
                .setAssetId(assetId)
                .setDescription(description)
                .volume = volume
        fees?.forEach { fee ->
            builder.addFees(fee.createGeneratedMessageBuilder())
        }
        return builder
    }

}