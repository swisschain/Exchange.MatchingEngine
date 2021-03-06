package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.utils.NumberUtils
import java.math.BigDecimal

data class WalletOperation(val brokerId: String,
                           val accountId: Long,
                           val walletId: Long,
                           val assetId: String,
                           val amount: BigDecimal,
                           val reservedAmount: BigDecimal = BigDecimal.ZERO,
                           val description: String? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WalletOperation

        if (brokerId != other.brokerId) return false
        if (accountId != other.accountId) return false
        if (walletId != other.walletId) return false
        if (assetId != other.assetId) return false
        if (!NumberUtils.equalsIgnoreScale(amount, other.amount)) return false
        if (!NumberUtils.equalsIgnoreScale(reservedAmount, other.reservedAmount)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = brokerId.hashCode()
        result = 31 * result + accountId.hashCode()
        result = 31 * result + walletId.hashCode()
        result = 31 * result + assetId.hashCode()
        result = 31 * result + amount.stripTrailingZeros().hashCode()
        result = 31 * result + reservedAmount.stripTrailingZeros().hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        return result
    }
}