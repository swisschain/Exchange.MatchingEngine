package com.swisschain.matching.engine.matching

import com.swisschain.matching.engine.daos.CopyWrapper
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.Order
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.outgoing.messages.LimitOrdersReport
import com.swisschain.matching.engine.outgoing.messages.TradeInfo
import java.math.BigDecimal
import java.util.concurrent.PriorityBlockingQueue

class MatchingResult(
        private val orderCopyWrapper: CopyWrapper<Order>,
        val cancelledLimitOrders: Set<CopyWrapper<LimitOrder>> = emptySet(),
        private val matchedOrders: List<CopyWrapper<LimitOrder>> = emptyList(),
        val skipLimitOrders: Set<LimitOrder> = emptySet(),
        val completedLimitOrders: List<CopyWrapper<LimitOrder>> = emptyList(),
        matchedUncompletedLimitOrderWrapper: CopyWrapper<LimitOrder>? = null,
        uncompletedLimitOrderWrapper: CopyWrapper<LimitOrder>? = null,
        val ownCashMovements: MutableList<WalletOperation> = mutableListOf(),
        val oppositeCashMovements: List<WalletOperation> = emptyList(),
        val marketOrderTrades: List<TradeInfo> = emptyList(),
        val limitOrdersReport: LimitOrdersReport? = null,
        val orderBook: PriorityBlockingQueue<LimitOrder> = PriorityBlockingQueue(),
        val marketBalance: BigDecimal? = null,
        val matchedWithZeroLatestTrade: Boolean = false,
        private val autoApply: Boolean = true
) {

    val orderCopy: Order = orderCopyWrapper.copy
    val uncompletedLimitOrderCopy: LimitOrder? = matchedUncompletedLimitOrderWrapper?.copy
    val uncompletedLimitOrder: LimitOrder? = uncompletedLimitOrderWrapper?.origin

    fun apply() {
        orderCopyWrapper.applyToOrigin()
        matchedOrders.forEach { it.applyToOrigin() }
    }

    init {
        if (this.autoApply) {
            apply()
        }
    }


}
