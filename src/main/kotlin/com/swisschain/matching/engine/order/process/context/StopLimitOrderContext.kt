package com.swisschain.matching.engine.order.process.context

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.order.transaction.ExecutionContext
import com.swisschain.matching.engine.services.validators.impl.OrderValidationResult
import com.swisschain.matching.engine.utils.NumberUtils
import java.math.BigDecimal

class StopLimitOrderContext(val order: LimitOrder,
                            val executionContext: ExecutionContext) {
    val limitAsset: Asset? = if (order.isBuySide()) {
        executionContext.assetPairsById[order.assetPairId]?.let { executionContext.assetsById[it.quotingAssetId] }
    } else {
        executionContext.assetPairsById[order.assetPairId]?.let { executionContext.assetsById[it.baseAssetId] }
    }
    val limitVolume: BigDecimal? = calculateLimitVolume()
    var validationResult: OrderValidationResult? = null
    var immediateExecutionPrice: BigDecimal? = null

    private fun calculateLimitVolume(): BigDecimal? {
        return if (order.isBuySide()) {
            val limitPrice = order.upperPrice ?: order.lowerPrice
            if (limitPrice != null)
                NumberUtils.setScaleRoundUp(order.volume * limitPrice, limitAsset!!.accuracy)
            else null
        } else order.getAbsVolume()
    }
}