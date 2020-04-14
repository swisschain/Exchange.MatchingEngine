package com.swisschain.matching.engine.services.validators.business

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.services.AssetOrderBook
import java.math.BigDecimal
import java.util.Date

interface LimitOrderBusinessValidator {
    fun performValidation(isTrustedClient: Boolean,
                          order: LimitOrder,
                          availableBalance: BigDecimal,
                          limitVolume: BigDecimal,
                          orderBook: AssetOrderBook,
                          date: Date,
                          currentOrderBookTotalSize: Int)
}