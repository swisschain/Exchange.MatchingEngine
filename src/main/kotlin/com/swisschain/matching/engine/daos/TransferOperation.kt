package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.daos.v2.FeeInstruction
import com.swisschain.matching.engine.utils.NumberUtils
import java.math.BigDecimal
import java.util.Date

data class TransferOperation(
        val matchingEngineOperationId: String,
        val brokerId: String,
        val externalId: String,
        val fromWalletId: String,
        val toWalletId: String,
        val asset: Asset,
        val dateTime: Date,
        val volume: BigDecimal,
        val overdraftLimit: BigDecimal?,
        val description: String,
        val fees: List<FeeInstruction>?) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransferOperation

        if (matchingEngineOperationId != other.matchingEngineOperationId) return false
        if (externalId != other.externalId) return false
        if (brokerId != other.brokerId) return false
        if (fromWalletId != other.fromWalletId) return false
        if (toWalletId != other.toWalletId) return false
        if (asset != other.asset) return false
        if (dateTime != other.dateTime) return false
        if (!NumberUtils.equalsIgnoreScale(volume, other.volume)) return false
        if (!NumberUtils.equalsIgnoreScale(overdraftLimit, other.overdraftLimit)) return false
        if (description != other.description) return false
        if (fees != other.fees) return false

        return true
    }

    override fun hashCode(): Int {
        var result = matchingEngineOperationId.hashCode()
        result = 31 * result + externalId.hashCode()
        result = 31 * result + brokerId.hashCode()
        result = 31 * result + fromWalletId.hashCode()
        result = 31 * result + toWalletId.hashCode()
        result = 31 * result + asset.hashCode()
        result = 31 * result + dateTime.hashCode()
        result = 31 * result + volume.stripTrailingZeros().hashCode()
        result = 31 * result + (overdraftLimit?.stripTrailingZeros()?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (fees?.hashCode() ?: 0)
        return result
    }
}