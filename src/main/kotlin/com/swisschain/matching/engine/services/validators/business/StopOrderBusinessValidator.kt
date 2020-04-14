package com.swisschain.matching.engine.services.validators.business

import com.swisschain.matching.engine.daos.LimitOrder
import java.math.BigDecimal
import java.util.Date

interface StopOrderBusinessValidator {
    fun performValidation(availableBalance: BigDecimal,
                          limitVolume: BigDecimal,
                          order: LimitOrder,
                          orderProcessingTime: Date,
                          currentOrderBookTotalSize: Int)
}