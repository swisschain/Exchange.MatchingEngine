package com.swisschain.matching.engine.outgoing.messages.v2.events.common

import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages

class ReservedBalanceUpdate(val brokerId: String,
                            val walletId: Long,
                            val assetId: String,
                            val volume: String) : EventPart<OutgoingMessages.ReservedBalanceUpdateEvent.ReservedBalanceUpdate.Builder> {

    override fun createGeneratedMessageBuilder(): OutgoingMessages.ReservedBalanceUpdateEvent.ReservedBalanceUpdate.Builder {
        val builder = OutgoingMessages.ReservedBalanceUpdateEvent.ReservedBalanceUpdate.newBuilder()
        builder.setWalletId(walletId)
                .setAssetId(assetId)
                .volume = volume
        return builder
    }

}