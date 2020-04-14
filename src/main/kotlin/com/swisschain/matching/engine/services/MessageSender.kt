package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingQueue

@Component
class MessageSender(private val clientsEventsQueue: BlockingQueue<Event>,
                    private val trustedClientsEventsQueue: BlockingQueue<ExecutionEvent>,
                    private val outgoingOrderBookQueue: BlockingQueue<OrderBookEvent>) {

    fun sendTrustedClientsMessage(message: ExecutionEvent) {
        trustedClientsEventsQueue.put(message)
    }

    fun sendMessage(message: Event) {
        clientsEventsQueue.put(message)
    }

    fun sendOrderBook(message: OrderBookEvent) {
        outgoingOrderBookQueue.put(message)
    }
}