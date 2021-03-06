package com.swisschain.matching.engine.services.validators.business.impl

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.holders.OrderBookMaxTotalSizeHolder
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.services.AssetOrderBook
import com.swisschain.matching.engine.services.validators.business.LimitOrderBusinessValidator
import com.swisschain.matching.engine.services.validators.common.OrderValidationUtils
import com.swisschain.matching.engine.services.validators.impl.OrderValidationException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Date

@Component
class LimitOrderBusinessValidatorImpl(private val orderBookMaxTotalSizeHolder: OrderBookMaxTotalSizeHolder)
    : LimitOrderBusinessValidator {

    override fun performValidation(isTrustedClient: Boolean, order: LimitOrder,
                                   availableBalance: BigDecimal,
                                   limitVolume: BigDecimal,
                                   orderBook: AssetOrderBook,
                                   date: Date,
                                   currentOrderBookTotalSize: Int) {
        OrderValidationUtils.validateOrderBookTotalSize(currentOrderBookTotalSize, orderBookMaxTotalSizeHolder.get())

        if (!isTrustedClient) {
            OrderValidationUtils.validateBalance(availableBalance, limitVolume)
        }

        validatePreviousOrderNotFound(order)
        validateNotEnoughFounds(order)
        OrderValidationUtils.validateExpiration(order, date)
    }

    private fun validatePreviousOrderNotFound(order: LimitOrder) {
        if (order.status == OrderStatus.NotFoundPrevious.name) {
            throw OrderValidationException(OrderStatus.NotFoundPrevious, "${orderInfo(order)} has not found previous order (${order.previousExternalId})")
        }
    }

    private fun validateNotEnoughFounds(order: LimitOrder) {
        if (order.status == OrderStatus.NotEnoughFunds.name) {
            throw OrderValidationException(OrderStatus.NotEnoughFunds, "${orderInfo(order)} has not enough funds")
        }
    }

    private fun orderInfo(order: LimitOrder) = "Limit order (id: ${order.externalId})"
}