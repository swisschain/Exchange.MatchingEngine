package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.google.protobuf.Any
import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.CashOut
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

class CashOutEvent(header: Header,
                   val balanceUpdates: List<BalanceUpdate>,
                   val cashOut: CashOut) : Event(header) {

    override fun buildGeneratedMessage(): OutgoingMessages.MessageWrapper {
        val builder = OutgoingMessages.CashOutEvent.newBuilder()
        builder.setHeader(header.createGeneratedMessageBuilder())
        balanceUpdates.forEach { balanceUpdate ->
            builder.addBalanceUpdates(balanceUpdate.createGeneratedMessageBuilder())
        }
        builder.setCashOut(cashOut.createGeneratedMessageBuilder())

        return OutgoingMessages.MessageWrapper.newBuilder().setMessageType(OutgoingMessages.MessageType.CASH_OUT_VALUE)
                .setMessage(Any.pack(builder.build())).build()
    }
}

