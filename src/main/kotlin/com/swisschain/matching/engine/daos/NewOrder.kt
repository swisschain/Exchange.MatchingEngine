package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.daos.fee.NewFeeInstruction
import com.swisschain.matching.engine.order.OrderStatus
import org.nustaq.serialization.annotations.Version
import java.io.Serializable
import java.util.Date

abstract class NewOrder(
        val id: String,
        val externalId: String,
        val assetPairId: String,
        val walletId: String,
        val volume: Double,
        status: String,
        val createdAt: Date,
        val registered: Date,
        var reservedLimitVolume: Double?,
        @Version (2)
        val fees: List<NewFeeInstruction>?,
        statusDate: Date?
) : Serializable, Copyable {

    var status = status
        private set

    @Version (4)
    var statusDate = statusDate
        private set

    open fun isBuySide(): Boolean {
        return volume > 0
    }

    abstract fun isOrigBuySide(): Boolean
    abstract fun isStraight(): Boolean
    abstract fun calculateReservedVolume(): Double
    abstract fun updateMatchTime(time: Date)
    abstract fun takePrice(): Double?
    abstract fun updatePrice(price: Double)
    abstract fun updateRemainingVolume(volume: Double)

    fun updateStatus(status: OrderStatus, date: Date) {
        if (status.name != this.status) {
            this.status = status.name
            this.statusDate = date
        }
    }

    override fun applyToOrigin(origin: Copyable) {
        origin as NewOrder
        origin.status = status
        origin.statusDate = statusDate
        origin.reservedLimitVolume = reservedLimitVolume
    }
}