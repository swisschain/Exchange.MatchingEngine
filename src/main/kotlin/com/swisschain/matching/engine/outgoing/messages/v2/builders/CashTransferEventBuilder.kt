package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.CashTransferEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.CashTransfer
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

class CashTransferEventBuilder : EventBuilder<CashTransferData, CashTransferEvent>() {

    private var balanceUpdates: List<BalanceUpdate>? = null
    private var cashTransfer: CashTransfer? = null

    override fun getMessageType() = MessageType.CASH_TRANSFER

    override fun setEventData(eventData: CashTransferData): EventBuilder<CashTransferData, CashTransferEvent> {
        balanceUpdates = convertBalanceUpdates(eventData.clientBalanceUpdates)
        cashTransfer = CashTransfer(eventData.transferOperation.fromWalletId,
                eventData.transferOperation.toWalletId,
                bigDecimalToString(eventData.transferOperation.volume)!!,
                bigDecimalToString(eventData.transferOperation.overdraftLimit),
                eventData.transferOperation.asset!!.assetId,
                convertFees(eventData.internalFees))
        return this
    }

    override fun buildEvent(header: Header) = CashTransferEvent(header, balanceUpdates!!, cashTransfer!!)

}