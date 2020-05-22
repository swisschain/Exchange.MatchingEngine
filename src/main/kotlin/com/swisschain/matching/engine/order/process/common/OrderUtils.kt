package com.swisschain.matching.engine.order.process.common

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.fee.v2.NewLimitOrderFeeInstruction
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.order.OrderStatus
import java.util.Date

class OrderUtils {
    companion object {
        fun createChildLimitOrder(order: LimitOrder,
                                  newOrderId: String,
                                  newOrderExternalId: String,
                                  date: Date): LimitOrder {
            return LimitOrder(newOrderId,
                    newOrderExternalId,
                    order.assetPairId,
                    order.brokerId,
                    order.accountId,
                    order.walletId,
                    order.volume,
                    order.price,
                    OrderStatus.InOrderBook.name,
                    date,
                    date,
                    date,
                    order.remainingVolume,
                    null,
                    null,
                    order.fees?.map { it as NewLimitOrderFeeInstruction },
                    LimitOrderType.LIMIT,
                    null,
                    null,
                    null,
                    null,
                    null,
                    order.timeInForce,
                    order.expiryTime,
                    order.externalId,
                    null)
        }
    }
}