package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.google.protobuf.Any
import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.ReservedBalanceUpdate

class ReservedBalanceUpdateEvent(header: Header,
                                 val balanceUpdates: List<BalanceUpdate>,
                                 val reservedBalanceUpdate: ReservedBalanceUpdate): Event(header) {

    override fun buildGeneratedMessage(): OutgoingMessages.MessageWrapper {
        val builder = OutgoingMessages.ReservedBalanceUpdateEvent.newBuilder()
        builder.setHeader(header.createGeneratedMessageBuilder())
        balanceUpdates.forEach { balanceUpdate ->
            builder.addBalanceUpdates(balanceUpdate.createGeneratedMessageBuilder())
        }
        builder.setReservedBalanceUpdate(reservedBalanceUpdate.createGeneratedMessageBuilder())
        return OutgoingMessages.MessageWrapper.newBuilder().setMessageType(OutgoingMessages.MessageType.RESERVED_BALANCE_UPDATE_VALUE)
                .setMessage(Any.pack(builder.build())).build()
    }
}