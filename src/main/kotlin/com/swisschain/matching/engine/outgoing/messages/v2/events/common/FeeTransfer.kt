package com.swisschain.matching.engine.outgoing.messages.v2.events.common

import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages

class FeeTransfer(val volume: String,
                  val sourceAccountId: Long,
                  val sourceWalletId: Long,
                  val targetAccountId: Long,
                  val targetWalletId: Long,
                  val assetId: String,
                  val feeCoef: String?,
                  val index: Int) : EventPart<OutgoingMessages.FeeTransfer.Builder> {

    override fun createGeneratedMessageBuilder(): OutgoingMessages.FeeTransfer.Builder {
        val builder = OutgoingMessages.FeeTransfer.newBuilder()
        builder.setVolume(volume)
                .setSourceAccountId(sourceAccountId)
                .setSourceWalletId(sourceWalletId)
                .setTargetAccountId(targetAccountId)
                .setTargetWalletId(targetWalletId)
                .assetId = assetId
        feeCoef?.let {
            builder.feeCoef = it
        }
        builder.index = index
        return builder
    }

}