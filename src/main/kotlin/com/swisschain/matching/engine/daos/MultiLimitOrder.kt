package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.order.OrderCancelMode

data class MultiLimitOrder(val messageUid: String,
                           val brokerId: String,
                           val walletId: Long,
                           val assetPairId: String,
                           val orders: Collection<LimitOrder>,
                           val cancelAllPreviousLimitOrders: Boolean,
                           val cancelBuySide: Boolean,
                           val cancelSellSide: Boolean,
                           val cancelMode: OrderCancelMode,
                           val buyReplacements: Map<String, LimitOrder>,
                           val sellReplacements: Map<String, LimitOrder>)