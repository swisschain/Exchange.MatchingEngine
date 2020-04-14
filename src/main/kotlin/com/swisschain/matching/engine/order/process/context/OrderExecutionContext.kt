package com.swisschain.matching.engine.order.process.context

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.Order
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.matching.MatchingResult
import com.swisschain.matching.engine.order.transaction.ExecutionContext

abstract class OrderExecutionContext<T: Order>(val order: T,
                                               val executionContext: ExecutionContext) {
    val oppositeLimitAsset: Asset? = if (order.isBuySide()) {
        executionContext.assetPairsById[order.assetPairId]?.let { executionContext.assetsById[it.baseAssetId] }
    } else {
        executionContext.assetPairsById[order.assetPairId]?.let { executionContext.assetsById[it.quotingAssetId] }
    }
    var matchingResult: MatchingResult? = null
    var cancelledOppositeClientsOrders: List<LimitOrder>? = null
    var cancelledOppositeTrustedClientsOrders: List<LimitOrder>? = null
    var cancelledOppositeOrdersWalletOperations: MutableList<WalletOperation>? = null
    var ownWalletOperations: MutableList<WalletOperation>? = null
    var isUncompletedOrderCancelled = false
}