package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.CashInEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.CashIn
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

class CashInEventBuilder : EventBuilder<CashInEventData, CashInEvent>() {

    private var balanceUpdates: List<BalanceUpdate>? = null
    private var cashIn: CashIn? = null

    override fun getMessageType() = MessageType.CASH_IN

    override fun setEventData(eventData: CashInEventData): EventBuilder<CashInEventData, CashInEvent> {
        balanceUpdates = convertBalanceUpdates(eventData.clientBalanceUpdates)
        cashIn = CashIn(eventData.cashInOperation.walletId,
                eventData.cashInOperation.assetId,
                bigDecimalToString(eventData.cashInOperation.amount)!!,
                convertFees(eventData.internalFees))
        return this
    }

    override fun buildEvent(header: Header) = CashInEvent(header, balanceUpdates!!, cashIn!!)

}