package com.swisschain.matching.engine.outgoing.messages.v2.events.common

import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages

class CashTransfer(val brokerId: String,
                   val fromWalletId: String,
                   val toWalletId: String,
                   val volume: String,
                   val overdraftLimit: String?,
                   val assetId: String,
                   val description: String,
                   val fees: List<Fee>?) : EventPart<OutgoingMessages.CashTransferEvent.CashTransfer.Builder> {

    override fun createGeneratedMessageBuilder(): OutgoingMessages.CashTransferEvent.CashTransfer.Builder {
        val builder = OutgoingMessages.CashTransferEvent.CashTransfer.newBuilder()
        builder.setBrokerId(brokerId)
                .setFromWalletId(fromWalletId)
                .setToWalletId(toWalletId)
                .setVolume(volume)
                .setOverdraftLimit(overdraftLimit?:"0.0")
                .setDescription(description)
                .assetId = assetId
        fees?.forEach { fee ->
            builder.addFees(fee.createGeneratedMessageBuilder())
        }
        return builder
    }

}