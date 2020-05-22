package com.swisschain.matching.engine.daos.fee.v2

import com.swisschain.matching.engine.daos.FeeSizeType
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.v2.FeeInstruction
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import java.math.BigDecimal

open class NewFeeInstruction(type: FeeType,
                             takerSizeType: FeeSizeType?,
                             takerSize: BigDecimal?,
                             sourceAccountId: Long?,
                             sourceWalletId: Long?,
                             targetAccountId: Long?,
                             targetWalletId: Long?,
                             val assetIds: List<String>) : FeeInstruction(type, takerSizeType, takerSize, sourceAccountId, sourceWalletId, targetAccountId, targetWalletId) {

    companion object {
        fun create(fees: List<IncomingMessages.Fee>): List<NewFeeInstruction> {
            return fees.map { create(it) }
        }

        fun create(fee: IncomingMessages.Fee): NewFeeInstruction {
            val feeType = FeeType.getByExternalId(fee.type)
            var sizeType: FeeSizeType? = if (fee.hasSizeType()) FeeSizeType.getByExternalId(fee.sizeType.value) else null
            if (feeType != FeeType.NO_FEE && sizeType == null) {
                sizeType = FeeSizeType.PERCENTAGE
            }
            return NewFeeInstruction(
                    feeType,
                    sizeType,
                    if (fee.hasSize()) BigDecimal(fee.size.value) else null,
                    if (fee.hasSourceAccountId()) fee.sourceAccountId.value else null,
                    if (fee.hasSourceWalletId()) fee.sourceWalletId.value else null,
                    if (fee.hasTargetAccountId()) fee.targetAccountId.value else null,
                    if (fee.hasTargetWalletId()) fee.targetWalletId.value else null,
                    fee.assetIdList.toList()
            )
        }
    }

    override fun toString(): String {
        return "NewFeeInstruction(type=$type" +
                (if (sizeType != null) ", sizeType=$sizeType" else "") +
                (if (size != null) ", size=${size.toPlainString()}" else "") +
                (if (assetIds.isNotEmpty()) ", assetIds=$assetIds" else "") +
                (if (sourceAccountId != null) ", sourceAccountId=$sourceAccountId" else "") +
                (if (sourceWalletId != null) ", sourceWalletId=$sourceWalletId" else "") +
                (if (targetWalletId != null) ", targetAccountId=$targetAccountId" else "") +
                "${if (targetWalletId != null) ", targetWalletId=$targetWalletId" else ""})"
    }

    override fun toNewFormat() = this
}