package com.swisschain.matching.engine.order.transaction

import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.order.SequenceNumbersWrapper
import com.swisschain.matching.engine.utils.event.isThereClientEvent
import com.swisschain.matching.engine.utils.event.isThereTrustedClientEvent
import org.springframework.stereotype.Component

@Component
class ExecutionEventsSequenceNumbersGenerator(private val messageSequenceNumberHolder: MessageSequenceNumberHolder) {

    fun generateSequenceNumbers(executionContext: ExecutionContext): SequenceNumbersWrapper {
        var sequenceNumber: Long? = null
        var clientsSequenceNumber: Long? = null
        var trustedClientsSequenceNumber: Long? = null
        if (isThereTrustedClientEvent(executionContext.getTrustedClientsLimitOrdersWithTrades())) {
            trustedClientsSequenceNumber = messageSequenceNumberHolder.getNewValue()
            sequenceNumber = trustedClientsSequenceNumber
        }
        if (isThereClientEvent(executionContext.getClientsLimitOrdersWithTrades(), executionContext.marketOrderWithTrades)) {
            clientsSequenceNumber = messageSequenceNumberHolder.getNewValue()
            sequenceNumber = clientsSequenceNumber
        }
        return SequenceNumbersWrapper(clientsSequenceNumber, trustedClientsSequenceNumber, sequenceNumber)
    }

}