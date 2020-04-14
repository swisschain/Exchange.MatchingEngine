package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.google.protobuf.Any
import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.OrderBook
import com.swisschain.matching.engine.outgoing.messages.v2.builders.bigDecimalToString
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

class OrderBookEvent(header: Header,
                     val orderBook: OrderBook) : Event(header) {

    override fun buildGeneratedMessage(): OutgoingMessages.MessageWrapper {
        val builder = OutgoingMessages.OrderBookSnapshot.newBuilder()
                .setAsset(orderBook.assetPair).setIsBuy(orderBook.isBuy).setTimestamp(orderBook.timestamp.createProtobufTimestampBuilder())
        orderBook.prices.forEach { orderBookPrice ->
            builder.addLevels(OutgoingMessages.OrderBookSnapshot.OrderBookLevel.newBuilder()
                    .setPrice(bigDecimalToString(orderBookPrice.price))
                    .setVolume(bigDecimalToString(orderBookPrice.volume))
                    .setWalletId(orderBookPrice.walletId)
                    .setOrderId(orderBookPrice.id).build())
        }
        val snapshotBuilder = OutgoingMessages.OrderBookSnapshotEvent.newBuilder()
                .setHeader(header.createGeneratedMessageBuilder()).setOrderBook(builder)
        return OutgoingMessages.MessageWrapper.newBuilder().setMessageType(OutgoingMessages.MessageType.ORDER_BOOK_SNAPSHOT_VALUE)
                .setMessage(Any.pack(snapshotBuilder.build())).build()
    }
}