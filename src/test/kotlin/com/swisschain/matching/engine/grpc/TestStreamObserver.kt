package com.swisschain.matching.engine.grpc

import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import io.grpc.stub.StreamObserver

class TestStreamObserver: StreamObserver<IncomingMessages.Response> {

    val responses = mutableListOf<IncomingMessages.Response>()

    override fun onNext(value: IncomingMessages.Response) {
        responses.add(value)
    }

    override fun onError(t: Throwable?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCompleted() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}