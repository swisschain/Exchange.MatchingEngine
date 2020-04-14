package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.google.protobuf.Any
import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.CashTransfer
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

class CashTransferEvent(header: Header,
                        val balanceUpdates: List<BalanceUpdate>,
                        val cashTransfer: CashTransfer) : Event(header) {

    override fun buildGeneratedMessage(): OutgoingMessages.MessageWrapper {
        val builder = OutgoingMessages.CashTransferEvent.newBuilder()
        builder.setHeader(header.createGeneratedMessageBuilder())
        balanceUpdates.forEach { balanceUpdate ->
            builder.addBalanceUpdates(balanceUpdate.createGeneratedMessageBuilder())
        }
        builder.setCashTransfer(cashTransfer.createGeneratedMessageBuilder())
        return OutgoingMessages.MessageWrapper.newBuilder().setMessageType(OutgoingMessages.MessageType.CASH_TRANSFER_VALUE)
                .setMessage(Any.pack(builder.build())).build()
    }
}