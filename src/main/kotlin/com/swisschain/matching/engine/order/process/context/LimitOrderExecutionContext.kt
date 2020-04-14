package com.swisschain.matching.engine.order.process.context

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.order.transaction.ExecutionContext
import com.swisschain.matching.engine.services.validators.impl.OrderValidationResult
import com.swisschain.matching.engine.utils.NumberUtils
import java.math.BigDecimal

class LimitOrderExecutionContext(order: LimitOrder,
                                 executionContext: ExecutionContext) : OrderExecutionContext<LimitOrder>(order, executionContext) {
    val limitAsset: Asset? = if (order.isBuySide()) {
        executionContext.assetPairsById[order.assetPairId]?.let { executionContext.assetsById[it.quotingAssetId] }
    } else {
        executionContext.assetPairsById[order.assetPairId]?.let { executionContext.assetsById[it.baseAssetId] }
    }
    var validationResult: OrderValidationResult? = null

    val limitVolume: BigDecimal? = if (order.isBuySide())
        NumberUtils.setScaleRoundUp(order.getAbsVolume() * order.price, limitAsset!!.accuracy)
    else
        order.getAbsVolume()

    var availableLimitAssetBalance: BigDecimal? = null
}