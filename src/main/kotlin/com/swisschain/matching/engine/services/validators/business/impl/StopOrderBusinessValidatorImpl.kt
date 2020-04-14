package com.swisschain.matching.engine.services.validators.business.impl

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.holders.OrderBookMaxTotalSizeHolder
import com.swisschain.matching.engine.services.validators.business.StopOrderBusinessValidator
import com.swisschain.matching.engine.services.validators.common.OrderValidationUtils
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Date

@Component
class StopOrderBusinessValidatorImpl(private val orderBookMaxTotalSizeHolder: OrderBookMaxTotalSizeHolder)
    : StopOrderBusinessValidator {
    override fun performValidation(availableBalance: BigDecimal,
                                   limitVolume: BigDecimal,
                                   order: LimitOrder,
                                   orderProcessingTime: Date,
                                   currentOrderBookTotalSize: Int) {
        OrderValidationUtils.validateOrderBookTotalSize(currentOrderBookTotalSize, orderBookMaxTotalSizeHolder.get())
        OrderValidationUtils.validateBalance(availableBalance, limitVolume)
        OrderValidationUtils.validateExpiration(order, orderProcessingTime)
    }
}