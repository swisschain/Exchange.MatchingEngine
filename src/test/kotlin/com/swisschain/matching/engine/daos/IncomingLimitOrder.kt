package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.daos.fee.v2.NewLimitOrderFeeInstruction
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.order.OrderTimeInForce
import java.util.Date
import java.util.UUID

data class IncomingLimitOrder(val volume: Double,
                              val price: Double? = null,
                              val uid: String? = UUID.randomUUID().toString(),
                              val feeInstructions: List<NewLimitOrderFeeInstruction> = emptyList(),
                              val oldUid: String? = null,
                              val timeInForce: OrderTimeInForce? = null,
                              val expiryTime: Date? = null,
                              val type: LimitOrderType? = null,
                              val lowerLimitPrice: Double? = null,
                              val lowerPrice: Double? = null,
                              val upperLimitPrice: Double? = null,
                              val upperPrice: Double? = null)