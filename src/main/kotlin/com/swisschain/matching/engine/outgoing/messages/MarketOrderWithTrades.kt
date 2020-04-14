package com.swisschain.matching.engine.outgoing.messages

import com.swisschain.matching.engine.daos.MarketOrder

class MarketOrderWithTrades (
    val messageId: String,
    val order: MarketOrder,
    val trades: List<TradeInfo> = emptyList()
)