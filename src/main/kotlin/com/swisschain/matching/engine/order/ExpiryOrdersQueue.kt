package com.swisschain.matching.engine.order

import com.swisschain.matching.engine.daos.LimitOrder
import org.springframework.stereotype.Component
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

@Component
class ExpiryOrdersQueue {

    private val ordersById = ConcurrentHashMap<String, ConcurrentHashMap<String, LimitOrder>>()

    fun addIfOrderHasExpiryTime(order: LimitOrder): Boolean {
        return if (order.hasExpiryTime()) {
            ordersById.getOrPut(order.brokerId) { ConcurrentHashMap() }[order.id] = order
            true
        } else false
    }

    fun removeIfOrderHasExpiryTime(order: LimitOrder): Boolean {
        return if (order.hasExpiryTime()) {
            ordersById[order.brokerId]?.remove(order.id)
            true
        } else false
    }

    fun getExpiredOrdersExternalIds(date: Date): Map<String, List<String>> {
        val result = HashMap<String, List<String>>()
        ordersById.entries.forEach { brokerOrders ->
            result[brokerOrders.key] = brokerOrders.value.values.asSequence()
                    .filter { it.isExpired(date) }
                    .map { it.externalId }
                    .toList()
        }
        return result
    }
}