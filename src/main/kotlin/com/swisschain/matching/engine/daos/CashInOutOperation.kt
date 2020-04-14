package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.utils.NumberUtils
import java.math.BigDecimal
import java.util.Date

data class CashInOutOperation(
        val matchingEngineOperationId: String,
        val externalId: String?,
        val brokerId: String,
        val walletId: String,
        val asset: Asset?,
        val dateTime: Date,
        val amount: BigDecimal,
        val feeInstructions: List<NewFeeInstruction>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CashInOutOperation

        if (matchingEngineOperationId != other.matchingEngineOperationId) return false
        if (externalId != other.externalId) return false
        if (walletId != other.walletId) return false
        if (brokerId != other.brokerId) return false
        if (asset != other.asset) return false
        if (dateTime != other.dateTime) return false
        if (!NumberUtils.equalsIgnoreScale(amount, other.amount)) return false
        if (feeInstructions != other.feeInstructions) return false
        return true
    }

    override fun hashCode(): Int {
        var result = matchingEngineOperationId.hashCode()
        result = 31 * result + (externalId?.hashCode() ?: 0)
        result = 31 * result + walletId.hashCode()
        result = 31 * result + brokerId.hashCode()
        result = 31 * result + (asset?.hashCode() ?: 0)
        result = 31 * result + dateTime.hashCode()
        result = 31 * result + amount.stripTrailingZeros().hashCode()
        result = 31 * result + feeInstructions.hashCode()
        return result
    }
}