package com.swisschain.matching.engine.outgoing.messages

import com.swisschain.matching.engine.daos.FeeTransfer
import com.swisschain.matching.engine.daos.fee.v2.Fee
import com.swisschain.matching.engine.daos.v2.FeeInstruction
import java.math.BigDecimal
import java.util.Date

class TradeInfo(
        val tradeId: String,
        val marketWalletId: Long,
        val marketVolume: String,
        val marketAsset: String,
        val limitWalletId: Long,
        val limitVolume: String,
        val limitAsset: String,
        val price: BigDecimal,
        val limitOrderId: String,
        val limitOrderExternalId: String,
        val timestamp: Date,
        val index: Long,
        val feeInstruction: FeeInstruction?,
        val feeTransfer: FeeTransfer?,
        val fees: List<Fee>,
        val absoluteSpread: BigDecimal?,
        val relativeSpread: BigDecimal?,
        @Transient
        val baseAssetId: String,
        @Transient
        val baseVolume: String,
        @Transient
        val quotingAssetId: String,
        @Transient
        val quotingVolume: String
)