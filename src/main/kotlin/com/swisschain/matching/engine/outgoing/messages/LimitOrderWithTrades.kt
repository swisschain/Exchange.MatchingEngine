package com.swisschain.matching.engine.outgoing.messages

import com.swisschain.matching.engine.daos.LimitOrder
import java.util.LinkedList

class LimitOrderWithTrades (
        val order: LimitOrder,
        val trades: MutableList<LimitTradeInfo> = LinkedList()
)