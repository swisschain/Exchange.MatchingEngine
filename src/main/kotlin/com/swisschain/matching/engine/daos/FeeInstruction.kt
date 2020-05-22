package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.daos.fee.NewFeeInstruction
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import java.io.Serializable
import java.math.BigDecimal

open class FeeInstruction(
        val type: FeeType,
        val sizeType: FeeSizeType?,
        val size: BigDecimal?,
        val sourceWalletId: Long?,
        val targetWalletId: Long?
) : Serializable {

    companion object {
        fun create(fee: IncomingMessages.Fee?): FeeInstruction? {
            if (fee == null) {
                return null
            }
            val feeType = FeeType.getByExternalId(fee.type)
            var sizeType: FeeSizeType? = if (fee.hasSizeType()) FeeSizeType.getByExternalId(fee.sizeType.value) else null
            if (feeType != FeeType.NO_FEE && sizeType == null) {
                sizeType = FeeSizeType.PERCENTAGE
            }
            return FeeInstruction(
                    feeType,
                    sizeType,
                    if (fee.hasSize()) BigDecimal(fee.size.value) else null,
                    if (fee.hasSourceWalletId()) fee.sourceWalletId.value else null,
                    if (fee.hasTargetWalletId()) fee.targetWalletId.value else null
            )
        }
    }

    override fun toString(): String {
        return "FeeInstruction(type=$type" +
                (if (sizeType != null) ", sizeType=$sizeType" else "") +
                (if (size != null) ", size=${size.toPlainString()}" else "") +
                (if (sourceWalletId != null) ", sourceWalletId=$sourceWalletId" else "") +
                "${if (targetWalletId != null) ", targetWalletId=$targetWalletId" else ""})"
    }

    open fun toNewFormat(): NewFeeInstruction = NewFeeInstruction(type, sizeType, size, sourceWalletId, targetWalletId, emptyList())
}