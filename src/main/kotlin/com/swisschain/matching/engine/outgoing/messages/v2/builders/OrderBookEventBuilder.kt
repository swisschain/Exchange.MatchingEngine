package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.outgoing.messages.OrderBook
import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

class OrderBookEventBuilder: EventBuilder<OrderBookEventData, OrderBookEvent>() {

    private var orderBook: OrderBook? = null

    override fun getMessageType() = MessageType.ORDER

    override fun setEventData(eventData: OrderBookEventData): OrderBookEventBuilder {
        orderBook = eventData.orderBook
        return this
    }

    override fun buildEvent(header: Header) = OrderBookEvent(header, orderBook!!)
}