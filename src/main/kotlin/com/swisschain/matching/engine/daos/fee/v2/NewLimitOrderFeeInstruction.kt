package com.swisschain.matching.engine.daos.fee.v2

import com.swisschain.matching.engine.daos.FeeSizeType
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import java.math.BigDecimal

class NewLimitOrderFeeInstruction(
        type: FeeType,
        takerSizeType: FeeSizeType?,
        takerSize: BigDecimal?,
        val makerSizeType: FeeSizeType?,
        val makerSize: BigDecimal?,
        sourceAccountId: Long?,
        sourceWalletId: Long?,
        targetAccountId: Long?,
        targetWalletId: Long?,
        assetIds: List<String>,
        val makerFeeModificator: BigDecimal?
) : NewFeeInstruction(type, takerSizeType, takerSize, sourceAccountId, sourceWalletId, targetAccountId, targetWalletId, assetIds) {

    companion object {
        fun create(fees: List<IncomingMessages.LimitOrderFee>): List<NewLimitOrderFeeInstruction> {
            return fees.map { create(it) }
        }

        fun create(fee: IncomingMessages.LimitOrderFee): NewLimitOrderFeeInstruction {
            val feeType = FeeType.getByExternalId(fee.type)
            var takerSizeType: FeeSizeType? = if (fee.hasTakerSize()) FeeSizeType.getByExternalId(fee.takerSizeType.value) else null
            var makerSizeType: FeeSizeType? = if (fee.hasMakerSizeType()) FeeSizeType.getByExternalId(fee.makerSizeType.value) else null
            if (feeType != FeeType.NO_FEE) {
                if (takerSizeType == null) {
                    takerSizeType = FeeSizeType.PERCENTAGE
                }
                if (makerSizeType == null) {
                    makerSizeType = FeeSizeType.PERCENTAGE
                }
            }
            return NewLimitOrderFeeInstruction(
                    feeType,
                    takerSizeType,
                    if (fee.hasTakerSize()) BigDecimal(fee.takerSize.value) else null,
                    makerSizeType,
                    if (fee.hasMakerSize()) BigDecimal(fee.makerSize.value) else null,
                    if (fee.hasSourceAccountId()) fee.sourceAccountId.value else null,
                    if (fee.hasSourceWalletId()) fee.sourceWalletId.value else null,
                    if (fee.hasTargetAccountId()) fee.targetAccountId.value else null,
                    if (fee.hasTargetWalletId()) fee.targetWalletId.value else null,
                    fee.assetIdList.toList(),
                    if (fee.hasMakerFeeModificator())  BigDecimal(fee.makerFeeModificator.value) else null)
        }
    }

    override fun toString(): String {
        return "NewLimitOrderFeeInstruction(type=$type" +
                (if (sizeType != null) ", takerSizeType=$sizeType" else "") +
                (if (size != null) ", takerSize=${size.toPlainString()}" else "") +
                (if (makerSizeType != null) ", makerSizeType=$makerSizeType" else "") +
                (if (makerSize != null) ", makerSize=${makerSize.toPlainString()}" else "") +
                (if (makerFeeModificator != null) ", makerFeeModificator=${makerFeeModificator.toPlainString()}" else "") +
                (if (assetIds.isNotEmpty()) ", assetIds=$assetIds" else "") +
                (if (sourceAccountId != null) ", sourceAccountId=$sourceAccountId" else "") +
                (if (sourceWalletId != null) ", sourceWalletId=$sourceWalletId" else "") +
                (if (targetAccountId != null) ", targetAccountId=$targetAccountId" else "") +
                "${if (targetWalletId != null) ", targetWalletId=$targetWalletId" else ""})"
    }

}