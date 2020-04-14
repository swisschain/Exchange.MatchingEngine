package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.utils.NumberUtils
import java.math.BigDecimal
import java.util.Date

class MarketOrder(id: String,
                  uid: String,
                  assetPairId: String,
                  brokerId: String,
                  walletId: String,
                  volume: BigDecimal,
                  var price: BigDecimal?,
                  status: String,
                  statusDate: Date,
                  createdAt: Date,
                  registered: Date?,
                  var matchedAt: Date?,
                  val straight: Boolean,
                  reservedLimitVolume: BigDecimal? = null,
                  fees: List<NewFeeInstruction>? = null)
    : Order(id, uid, assetPairId, brokerId, walletId, volume, status, createdAt, registered, reservedLimitVolume, fees, statusDate) {

    override fun isOrigBuySide(): Boolean {
        return super.isBuySide()
    }

    override fun isBuySide(): Boolean {
        return if (straight) super.isBuySide() else !super.isBuySide()
    }

    override fun isStraight(): Boolean {
        return straight
    }

    override fun calculateReservedVolume(): BigDecimal {
        return reservedLimitVolume ?: BigDecimal.ZERO
    }

    override fun updateMatchTime(time: Date) {
        matchedAt = time
    }

    override fun takePrice(): BigDecimal? {
        return price
    }

    override fun updatePrice(price: BigDecimal) {
        this.price = price
    }

    override fun updateRemainingVolume(volume: BigDecimal) {
        //nothing to do
    }

    override fun copy(): MarketOrder {
        return MarketOrder(id, externalId, assetPairId, brokerId, walletId, volume, price, status, statusDate!!,
                createdAt, registered, matchedAt, straight, reservedLimitVolume, fees)
    }

    override fun applyToOrigin(origin: Copyable) {
        super.applyToOrigin(origin)
        origin as MarketOrder
        origin.price = price
        origin.matchedAt = matchedAt
    }

    override fun toString(): String {
        return "market order id: $id, broker id: ${brokerId}, client: $walletId, asset: $assetPairId, " +
                "volume: ${NumberUtils.roundForPrint(volume)}, straight: $straight"
    }
}