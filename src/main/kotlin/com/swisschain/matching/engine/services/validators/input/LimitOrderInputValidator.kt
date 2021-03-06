package com.swisschain.matching.engine.services.validators.input

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.incoming.parsers.data.SingleLimitOrderParsedData
import com.swisschain.matching.engine.order.process.context.StopLimitOrderContext

interface LimitOrderInputValidator {
    fun validateLimitOrder(singleLimitOrderParsedData: SingleLimitOrderParsedData)
    fun validateStopOrder(singleLimitOrderParsedData: SingleLimitOrderParsedData)
    fun validateLimitOrder(isTrustedClient: Boolean,
                           order: LimitOrder,
                           assetPair: AssetPair?,
                           assetPairId: String,
                           baseAsset: Asset?)
    fun validateStopOrder(stopLimitOrderContext: StopLimitOrderContext)
}