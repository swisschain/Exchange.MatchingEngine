package com.swisschain.matching.engine.order.process.context

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.order.transaction.ExecutionContext

class PreviousLimitOrdersContext(val brokerId: String,
                                 val walletId: Long,
                                 val assetPairId: String,
                                 val cancelAllPreviousLimitOrders: Boolean,
                                 val cancelBuySide: Boolean,
                                 val cancelSellSide: Boolean,
                                 val buyReplacementsByPreviousExternalId: Map<String, LimitOrder>,
                                 val sellReplacementsByPreviousExternalId: Map<String, LimitOrder>,
                                 val executionContext: ExecutionContext) {
    var buyOrdersToCancel: Collection<LimitOrder>? = null
    var sellOrdersToCancel: Collection<LimitOrder>? = null
    var buyStopOrdersToCancel: Collection<LimitOrder>? = null
    var sellStopOrdersToCancel: Collection<LimitOrder>? = null
}