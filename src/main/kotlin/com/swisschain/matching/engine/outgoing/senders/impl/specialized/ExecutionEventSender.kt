package com.swisschain.matching.engine.outgoing.senders.impl.specialized

import com.swisschain.matching.engine.daos.ExecutionData
import com.swisschain.matching.engine.outgoing.messages.OrderBook
import com.swisschain.matching.engine.outgoing.messages.v2.builders.EventFactory
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSender
import com.swisschain.matching.engine.services.AssetOrderBook
import com.swisschain.matching.engine.services.MessageSender
import com.swisschain.matching.engine.utils.event.isThereClientEvent
import com.swisschain.matching.engine.utils.event.isThereTrustedClientEvent
import org.springframework.stereotype.Component

@Component
class ExecutionEventSender(private val messageSender: MessageSender) : SpecializedEventSender<ExecutionData> {

    override fun getEventClass(): Class<ExecutionData> {
        return ExecutionData::class.java
    }

    override fun sendEvent(event: ExecutionData) {
        sendTrustedClientsExecutionEventIfNeeded(event)
        sendClientsExecutionEventIfNeeded(event)
        sendOrderBooksEvents(event)
    }

    private fun sendClientsExecutionEventIfNeeded(executionData: ExecutionData) {
        val executionContext = executionData.executionContext

        val clientsLimitOrdersWithTrades = executionContext.getClientsLimitOrdersWithTrades().toList()
        if (isThereClientEvent(clientsLimitOrdersWithTrades, executionContext.marketOrderWithTrades)) {
            messageSender.sendMessage(EventFactory.createExecutionEvent(sequenceNumber = executionData.sequenceNumbers.clientsSequenceNumber!!,
                    messageId = executionContext.messageId,
                    requestId = executionContext.requestId,
                    date = executionContext.date,
                    messageType = executionContext.messageType,
                    clientBalanceUpdates = executionContext.walletOperationsProcessor.getClientBalanceUpdates(),
                    limitOrdersWithTrades = clientsLimitOrdersWithTrades,
                    marketOrderWithTrades = executionContext.marketOrderWithTrades))
        }
    }

    private fun sendTrustedClientsExecutionEventIfNeeded(executionData: ExecutionData) {
        val executionContext = executionData.executionContext

        val trustedClientsLimitOrdersWithTrades = executionContext.getTrustedClientsLimitOrdersWithTrades().toList()
        if (isThereTrustedClientEvent(trustedClientsLimitOrdersWithTrades)) {
            messageSender.sendTrustedClientsMessage(EventFactory.createTrustedClientsExecutionEvent(sequenceNumber = executionData.sequenceNumbers.trustedClientsSequenceNumber!!,
                    messageId = executionContext.messageId,
                    requestId = executionContext.requestId,
                    date = executionContext.date,
                    messageType = executionContext.messageType,
                    limitOrdersWithTrades = trustedClientsLimitOrdersWithTrades))
        }
    }

    private fun sendOrderBooksEvents(executionData: ExecutionData) {
        val executionContext = executionData.executionContext
        executionContext.orderBooksHolder.outgoingOrderBooks.forEach {
            val orderBook = OrderBook(it.brokerId, it.assetPair, it.isBuySide, it.date, AssetOrderBook.sort(it.isBuySide, it.volumePrices))
            messageSender.sendOrderBook(EventFactory.createOrderBookEvent(sequenceNumber = executionData.sequenceNumbers.sequenceNumber!!,
                    messageId = executionContext.messageId,
                    requestId = executionContext.requestId,
                    date = executionContext.date,
                    messageType = executionContext.messageType,
                    orderBook = orderBook))
        }
    }
}