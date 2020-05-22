package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.ReservedBalanceUpdateEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.ReservedBalanceUpdate

class ReservedBalanceUpdateEventBuilder : EventBuilder<ReservedBalanceUpdateEventData, ReservedBalanceUpdateEvent>() {

    private var balanceUpdates: List<BalanceUpdate>? = null
    private var reservedBalanceUpdate: ReservedBalanceUpdate? = null

    override fun getMessageType() = MessageType.RESERVED_BALANCE_UPDATE

    override fun setEventData(eventData: ReservedBalanceUpdateEventData): EventBuilder<ReservedBalanceUpdateEventData, ReservedBalanceUpdateEvent> {
        balanceUpdates = convertBalanceUpdates(eventData.clientBalanceUpdates)
        reservedBalanceUpdate = ReservedBalanceUpdate(eventData.reservedBalanceUpdateOperation.brokerId,
                eventData.reservedBalanceUpdateOperation.accountId!!,
                eventData.reservedBalanceUpdateOperation.walletId,
                eventData.reservedBalanceUpdateOperation.assetId,
                bigDecimalToString(eventData.reservedBalanceUpdateOperation.reservedAmount)!!)
        return this
    }

    override fun buildEvent(header: Header) = ReservedBalanceUpdateEvent(header, balanceUpdates!!, reservedBalanceUpdate!!)
}