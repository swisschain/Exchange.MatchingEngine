package com.swisschain.matching.engine.utils.order

import com.swisschain.matching.engine.holders.OrderBookMaxTotalSizeHolder
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.services.GenericStopLimitOrderService
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class OrderBookTotalSizeInitialChecker(private val genericLimitOrderService: GenericLimitOrderService,
                                       private val genericStopLimitOrderService: GenericStopLimitOrderService,
                                       private val orderBookMaxTotalSizeHolder: OrderBookMaxTotalSizeHolder) {

    @PostConstruct
    fun check() {
        val orderBookMaxTotalSize = orderBookMaxTotalSizeHolder.get() ?: return
        val orderBookTotalSize = genericLimitOrderService.getTotalSize(null) + genericStopLimitOrderService.getTotalSize(null)
        if (orderBookMaxTotalSize < orderBookTotalSize) {
            throw IllegalStateException("Current order book total size ($orderBookTotalSize) is greater than configured maximum size ($orderBookMaxTotalSize)")
        }
    }
}