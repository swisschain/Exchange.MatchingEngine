package com.swisschain.matching.engine.outgoing.senders

import com.swisschain.matching.engine.daos.ExecutionData
import com.swisschain.matching.engine.outgoing.messages.CashInOutEventData
import com.swisschain.matching.engine.outgoing.messages.CashTransferEventData
import com.swisschain.matching.engine.outgoing.messages.ReservedCashInOutEventData

interface OutgoingEventProcessor {
    fun submitCashTransferEvent(cashTransferEventData: CashTransferEventData)
    fun submitCashInOutEvent(cashInOutEventData: CashInOutEventData)
    fun submitExecutionEvent(executionEventData: ExecutionData)
    fun submitReservedCashInOutEvent(reservedCashInOutEventData: ReservedCashInOutEventData)
}