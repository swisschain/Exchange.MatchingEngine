package com.swisschain.matching.engine.outgoing.senders.impl.specialized

import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.outgoing.messages.CashTransferEventData
import com.swisschain.matching.engine.outgoing.messages.v2.builders.EventFactory
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSender
import com.swisschain.matching.engine.services.MessageSender
import org.springframework.stereotype.Component

@Component
class CashTransferEventSender(val messageSender: MessageSender) : SpecializedEventSender<CashTransferEventData> {
    override fun getEventClass(): Class<CashTransferEventData> {
        return CashTransferEventData::class.java
    }

    override fun sendEvent(event: CashTransferEventData) {
        val outgoingMessage = EventFactory.createCashTransferEvent(event.sequenceNumber,
                event.messageId,
                event.transferOperation.externalId,
                event.now,
                MessageType.CASH_TRANSFER_OPERATION,
                event.walletProcessor.getClientBalanceUpdates(),
                event.transferOperation,
                event.fees)

        messageSender.sendMessage(outgoingMessage)
    }
}