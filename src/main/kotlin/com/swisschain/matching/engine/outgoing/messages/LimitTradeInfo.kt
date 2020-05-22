package com.swisschain.matching.engine.outgoing.messages

import com.swisschain.matching.engine.daos.FeeTransfer
import com.swisschain.matching.engine.daos.fee.v2.Fee
import com.swisschain.matching.engine.daos.v2.FeeInstruction
import com.swisschain.matching.engine.outgoing.messages.v2.enums.TradeRole
import java.math.BigDecimal
import java.util.Date

class LimitTradeInfo(
        val tradeId: String,
        val walletId: Long,
        val asset: String,
        val volume: String,
        val price: BigDecimal,
        val timestamp: Date,

        val oppositeOrderId: String,
        val oppositeOrderExternalId: String,
        val oppositeAsset: String,
        val oppositeWalletId: Long,
        val oppositeVolume: String,
        val index: Long,
        val feeInstruction: FeeInstruction?,
        val feeTransfer: FeeTransfer?,
        val fees: List<Fee>,
        val absoluteSpread: BigDecimal?,
        val relativeSpread: BigDecimal?,
        @Transient
        val role: TradeRole,
        @Transient
        val baseAssetId: String,
        @Transient
        val baseVolume: String,
        @Transient
        val quotingAssetId: String,
        @Transient
        val quotingVolume: String
)