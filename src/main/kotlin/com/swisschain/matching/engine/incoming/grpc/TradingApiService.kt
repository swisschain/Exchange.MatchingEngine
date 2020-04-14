package com.swisschain.matching.engine.incoming.grpc

import com.swisschain.matching.engine.incoming.MessageRouter
import com.swisschain.matching.engine.incoming.TradingServiceGrpc
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MarketOrderMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MultiLimitOrderMessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import io.grpc.stub.StreamObserver

class TradingApiService(private val messageRouter: MessageRouter): TradingServiceGrpc.TradingServiceImplBase() {

    override fun marketOrder(request: IncomingMessages.MarketOrder?, responseObserver: StreamObserver<IncomingMessages.MarketOrderResponse>?) {
        if (request != null) {
            messageRouter.process(MarketOrderMessageWrapper(MessageType.MARKET_ORDER.type, request, responseObserver,true))
        }
    }

    override fun limitOrder(request: IncomingMessages.LimitOrder?, responseObserver: StreamObserver<IncomingMessages.Response>?) {
        if (request != null) {
            messageRouter.process(GenericMessageWrapper(MessageType.LIMIT_ORDER.type, request, responseObserver, true))
        }
    }

    override fun cancelLimitOrder(request: IncomingMessages.LimitOrderCancel?, responseObserver: StreamObserver<IncomingMessages.Response>?) {
        if (request != null) {
            messageRouter.process(GenericMessageWrapper(MessageType.LIMIT_ORDER_CANCEL.type, request, responseObserver, true))
        }
    }

    override fun massCancelLimitOrder(request: IncomingMessages.LimitOrderMassCancel?, responseObserver: StreamObserver<IncomingMessages.Response>?) {
        if (request != null) {
            messageRouter.process(GenericMessageWrapper(MessageType.LIMIT_ORDER_MASS_CANCEL.type, request, responseObserver, true))
        }
    }

    override fun multiLimitOrder(request: IncomingMessages.MultiLimitOrder?, responseObserver: StreamObserver<IncomingMessages.MultiLimitOrderResponse>?) {
        if (request != null) {
            messageRouter.process(MultiLimitOrderMessageWrapper(MessageType.MULTI_LIMIT_ORDER.type, request, responseObserver,true))
        }
    }
}