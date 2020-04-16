package com.swisschain.matching.engine.incoming.grpc

import com.google.protobuf.Empty
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.incoming.OrderBooksServiceGrpc
import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.OrderBook
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.utils.NumberUtils
import io.grpc.stub.StreamObserver
import java.util.Date

class OrderBooksService(
        private val genericLimitOrderService: GenericLimitOrderService,
        private val assetsCache: AssetsHolder,
        private val assetPairsCache: AssetsPairsHolder): OrderBooksServiceGrpc.OrderBooksServiceImplBase() {

    override fun orderBookSnapshots(request: Empty?, responseObserver: StreamObserver<OutgoingMessages.OrderBookSnapshot>?) {
        if (responseObserver != null) {
            val now = Date()
            val orderBooks = genericLimitOrderService.getAllOrderBooks()
            orderBooks.values.forEach { brokerOrderBooks ->
                brokerOrderBooks.values.forEach {
                    val orderBook = it.copy()
                    responseObserver.onNext(buildOrderBook(OrderBook(orderBook.brokerId, orderBook.assetPairId, true, now, orderBook.getOrderBook(true))))
                    responseObserver.onNext(buildOrderBook(OrderBook(orderBook.brokerId, orderBook.assetPairId, false, now, orderBook.getOrderBook(false))))
                }
            }
            responseObserver.onCompleted()
        }
    }

    override fun brokerOrderBookSnapshots(request: OutgoingMessages.OrderBookSnapshotRequest, responseObserver: StreamObserver<OutgoingMessages.OrderBookSnapshot>?) {
        if (responseObserver != null) {
            val now = Date()
            val orderBooks = genericLimitOrderService.getAllOrderBooks(request.brokerId)
            orderBooks?.values?.forEach {
                val orderBook = it.copy()
                responseObserver.onNext(buildOrderBook(OrderBook(orderBook.brokerId, orderBook.assetPairId, true, now, orderBook.getOrderBook(true))))
                responseObserver.onNext(buildOrderBook(OrderBook(orderBook.brokerId, orderBook.assetPairId, false, now, orderBook.getOrderBook(false))))
            }
            responseObserver.onCompleted()
        }
    }

    private fun buildOrderBook(orderBook: OrderBook): OutgoingMessages.OrderBookSnapshot {
        val builder = OutgoingMessages.OrderBookSnapshot.newBuilder()
                .setBrokerId(orderBook.brokerId)
                .setAsset(orderBook.assetPair).setIsBuy(orderBook.isBuy).setTimestamp(orderBook.timestamp.createProtobufTimestampBuilder())
        val pair = assetPairsCache.getAssetPair(orderBook.brokerId, orderBook.assetPair)
        val baseAsset = assetsCache.getAsset(orderBook.brokerId, pair.baseAssetId)
        orderBook.prices.forEach { orderBookPrice ->
            builder.addLevels(OutgoingMessages.OrderBookSnapshot.OrderBookLevel.newBuilder()
                    .setPrice(NumberUtils.setScaleRoundHalfUp(orderBookPrice.price, pair.accuracy).toPlainString())
                    .setVolume(NumberUtils.setScaleRoundHalfUp(orderBookPrice.volume, baseAsset.accuracy).toPlainString())
                    .setWalletId(orderBookPrice.walletId)
                    .setOrderId(orderBookPrice.id).build())
        }
        return builder.build()
    }
}