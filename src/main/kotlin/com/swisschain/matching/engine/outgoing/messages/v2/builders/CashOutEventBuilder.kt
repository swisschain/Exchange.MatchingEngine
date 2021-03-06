package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.CashOutEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.CashOut
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

class CashOutEventBuilder : EventBuilder<CashOutEventData, CashOutEvent>() {

    private var balanceUpdates: List<BalanceUpdate>? = null
    private var cashOut: CashOut? = null

    override fun getMessageType() = MessageType.CASH_OUT

    override fun setEventData(eventData: CashOutEventData): EventBuilder<CashOutEventData, CashOutEvent> {
        balanceUpdates = convertBalanceUpdates(eventData.clientBalanceUpdates)
        cashOut = CashOut(eventData.cashOutOperation.brokerId,
                eventData.cashOutOperation.accountId!!,
                eventData.cashOutOperation.walletId,
                eventData.cashOutOperation.assetId,
                bigDecimalToString(eventData.cashOutOperation.amount.abs())!!,
                eventData.cashOutOperation.description?: "",
                convertFees(eventData.internalFees))
        return this
    }

    override fun buildEvent(header: Header) = CashOutEvent(header, balanceUpdates!!, cashOut!!)

}