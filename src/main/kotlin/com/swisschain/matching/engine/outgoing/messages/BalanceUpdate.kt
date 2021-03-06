package com.swisschain.matching.engine.outgoing.messages

import java.math.BigDecimal
import java.util.Date

data class BalanceUpdate( val id: String,
                     val type: String,
                     val timestamp: Date,
                     var balances: List<ClientBalanceUpdate>,
                     val messageId: String)

data class ClientBalanceUpdate(
        val brokerId: String,
        val accountId: Long,
        val walletId: Long,
        val asset: String,
        val oldBalance: BigDecimal,
        var newBalance: BigDecimal,
        val oldReserved: BigDecimal,
        var newReserved: BigDecimal
)