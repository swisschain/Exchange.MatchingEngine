package com.swisschain.matching.engine.outgoing.messages

import com.fasterxml.jackson.annotation.JsonProperty
import com.swisschain.matching.engine.daos.LimitOrder
import java.math.BigDecimal
import java.util.ArrayList
import java.util.Date
import java.util.concurrent.PriorityBlockingQueue

class OrderBook {
    val assetPair: String
    val brokerId: String

    @get:JsonProperty("isBuy")
    @JsonProperty("isBuy")
    val isBuy: Boolean

    val timestamp: Date

    val prices: MutableList<Order> = ArrayList()

    constructor(brokerId: String, assetPair: String, isBuy: Boolean, timestamp: Date) {
        this.brokerId = brokerId
        this.assetPair = assetPair
        this.isBuy = isBuy
        this.timestamp = timestamp
    }

    constructor(brokerId: String, assetPair: String, isBuy: Boolean, timestamp: Date, orders: PriorityBlockingQueue<LimitOrder>) {
        this.brokerId = brokerId
        this.assetPair = assetPair
        this.isBuy = isBuy
        this.timestamp = timestamp

        while (!orders.isEmpty()) {
            val order = orders.poll()
            addVolumePrice(order.externalId, order.walletId, order.remainingVolume, order.price)
        }
    }

    constructor(brokerId: String, assetPair: String, isBuy: Boolean, timestamp: Date, orders: Array<LimitOrder>) {
        this.brokerId = brokerId
        this.assetPair = assetPair
        this.isBuy = isBuy
        this.timestamp = timestamp

        for (order in orders) {
            addVolumePrice(order.externalId, order.walletId, order.remainingVolume, order.price)
        }
    }

    private fun addVolumePrice(id: String, walletId: Long, volume: BigDecimal, price: BigDecimal) {
        prices.add(Order(id, walletId, volume, price))
    }
}

data class Order(val id: String, val walletId: Long, val volume: BigDecimal, val price: BigDecimal)
