package com.swisschain.matching.engine.daos.fee.v2

import com.swisschain.matching.engine.daos.FeeSizeType
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.v2.FeeInstruction
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import java.math.BigDecimal

open class NewFeeInstruction(type: FeeType,
                             takerSizeType: FeeSizeType?,
                             takerSize: BigDecimal?,
                             sourceWalletId: String?,
                             targetWalletId: String?,
                             val assetIds: List<String>) : FeeInstruction(type, takerSizeType, takerSize, sourceWalletId, targetWalletId) {

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
                    if (fee.hasSourceWalletId()) fee.sourceWalletId.value else null,
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
                (if (sourceWalletId?.isNotEmpty() == true) ", sourceWalletId=$sourceWalletId" else "") +
                "${if (targetWalletId?.isNotEmpty() == true) ", targetWalletId=$targetWalletId" else ""})"
    }

    override fun toNewFormat() = this
}