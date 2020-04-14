package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Order

class ExecutionEventBuilder: EventBuilder<ExecutionEventData, ExecutionEvent>() {

    private var balanceUpdates: List<BalanceUpdate>? = null
    private var orders: List<Order>? = null

    override fun getMessageType() = MessageType.ORDER

    override fun setEventData(eventData: ExecutionEventData): EventBuilder<ExecutionEventData, ExecutionEvent> {
        balanceUpdates = convertBalanceUpdates(eventData.clientBalanceUpdates)
        orders = convertOrders(eventData.limitOrdersWithTrades, eventData.marketOrderWithTrades)
        return this
    }

    override fun buildEvent(header: Header) = ExecutionEvent(header, balanceUpdates, orders!!)
}