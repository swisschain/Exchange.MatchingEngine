package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.google.protobuf.Any
import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Order

class ExecutionEvent(header: Header,
                     val balanceUpdates: List<BalanceUpdate>?,
                     val orders: List<Order>) : Event(header) {

    override fun buildGeneratedMessage(): OutgoingMessages.MessageWrapper {
        val builder = OutgoingMessages.ExecutionEvent.newBuilder()
        builder.setHeader(header.createGeneratedMessageBuilder())
        balanceUpdates?.forEach { balanceUpdate ->
            builder.addBalanceUpdates(balanceUpdate.createGeneratedMessageBuilder())
        }
        orders.forEach { order ->
            builder.addOrders(order.createGeneratedMessageBuilder())
        }
        return OutgoingMessages.MessageWrapper.newBuilder().setMessageType(OutgoingMessages.MessageType.ORDER_VALUE)
                .setMessage(Any.pack(builder.build())).build()
    }
}