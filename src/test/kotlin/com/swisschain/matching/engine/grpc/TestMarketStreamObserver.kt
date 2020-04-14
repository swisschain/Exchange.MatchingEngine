package com.swisschain.matching.engine.grpc

import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import io.grpc.stub.StreamObserver

class TestMarketStreamObserver: StreamObserver<IncomingMessages.MarketOrderResponse> {

    val responses = mutableListOf<IncomingMessages.MarketOrderResponse>()

    override fun onNext(value: IncomingMessages.MarketOrderResponse) {
        responses.add(value)
    }

    override fun onError(t: Throwable?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCompleted() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}