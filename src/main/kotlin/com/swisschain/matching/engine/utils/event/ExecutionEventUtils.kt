package com.swisschain.matching.engine.utils.event

import com.swisschain.matching.engine.outgoing.messages.LimitOrderWithTrades
import com.swisschain.matching.engine.outgoing.messages.MarketOrderWithTrades

fun isThereTrustedClientEvent(trustedClientsLimitOrdersWithTrades: Collection<LimitOrderWithTrades>): Boolean {
    return trustedClientsLimitOrdersWithTrades.isNotEmpty()
}

fun isThereClientEvent(clientsLimitOrdersWithTrades: Collection<LimitOrderWithTrades>,
                       marketOrderWithTrades: MarketOrderWithTrades?): Boolean {
    return clientsLimitOrdersWithTrades.isNotEmpty() || marketOrderWithTrades != null
}