package com.swisschain.matching.engine.services.validators.common

import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.Order
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.services.validators.impl.OrderValidationException
import com.swisschain.utils.logging.MetricsLogger
import java.math.BigDecimal
import java.util.Date

class OrderValidationUtils {
    companion object {

        private val METRICS_LOGGER = MetricsLogger.getLogger()

        fun checkMinVolume(order: Order, assetPair: AssetPair): Boolean {
            return  order.getAbsVolume() >= assetPair.minVolume
        }

        fun validateBalance(availableBalance: BigDecimal, limitVolume: BigDecimal) {
            if (availableBalance < limitVolume) {
                throw OrderValidationException(OrderStatus.NotEnoughFunds, "not enough funds to reserve")
            }
        }

        fun validateExpiration(order: LimitOrder, orderProcessingTime: Date) {
            if (order.isExpired(orderProcessingTime)) {
                throw OrderValidationException(OrderStatus.Cancelled, "expired")
            }
        }

        fun validateOrderBookTotalSize(currentOrderBookTotalSize: Int, orderBookMaxTotalSize: Int?) {
            if (orderBookMaxTotalSize != null && currentOrderBookTotalSize >= orderBookMaxTotalSize) {
                val errorMessage = "Order book max total size reached (current: $currentOrderBookTotalSize, max: $orderBookMaxTotalSize)"
                METRICS_LOGGER.logWarning(errorMessage)
                throw OrderValidationException(OrderStatus.OrderBookMaxSizeReached, errorMessage)
            }
        }
    }
}