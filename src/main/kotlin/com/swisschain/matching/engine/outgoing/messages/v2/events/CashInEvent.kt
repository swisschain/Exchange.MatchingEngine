package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.google.protobuf.Any
import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.CashIn
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

class CashInEvent(header: Header,
                  val balanceUpdates: List<BalanceUpdate>,
                  val cashIn: CashIn) : Event(header) {

    override fun buildGeneratedMessage(): OutgoingMessages.MessageWrapper {
        val builder = OutgoingMessages.CashInEvent.newBuilder()
        builder.setHeader(header.createGeneratedMessageBuilder())
        balanceUpdates.forEach { balanceUpdate ->
            builder.addBalanceUpdates(balanceUpdate.createGeneratedMessageBuilder())
        }
        builder.setCashIn(cashIn.createGeneratedMessageBuilder())
        return OutgoingMessages.MessageWrapper.newBuilder().setMessageType(OutgoingMessages.MessageType.CASH_IN_VALUE)
                .setMessage(Any.pack(builder.build())).build()
    }
}