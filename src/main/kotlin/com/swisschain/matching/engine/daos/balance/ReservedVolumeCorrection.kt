package com.swisschain.matching.engine.daos.balance

import com.swisschain.matching.engine.utils.NumberUtils
import java.math.BigDecimal
import java.util.Date

data class ReservedVolumeCorrection(val timestamp: Date,
                                    val walletId: Long,
                                    val assetId: String,
                                    val orderIds: String?,
                                    val oldReserved: BigDecimal,
                                    val newReserved: BigDecimal) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReservedVolumeCorrection

        if (timestamp != other.timestamp) return false
        if (walletId != other.walletId) return false
        if (assetId != other.assetId) return false
        if (orderIds != other.orderIds) return false
        if (!NumberUtils.equalsIgnoreScale(oldReserved, other.oldReserved)) return false
        if (!NumberUtils.equalsIgnoreScale(newReserved, other.newReserved)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + walletId.hashCode()
        result = 31 * result + assetId.hashCode()
        result = 31 * result + (orderIds?.hashCode() ?: 0)
        result = 31 * result + oldReserved.stripTrailingZeros().hashCode()
        result = 31 * result + newReserved.stripTrailingZeros().hashCode()
        return result
    }
}