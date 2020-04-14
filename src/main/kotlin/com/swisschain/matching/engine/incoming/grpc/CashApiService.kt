package com.swisschain.matching.engine.incoming.grpc

import com.swisschain.matching.engine.incoming.CashServiceGrpc
import com.swisschain.matching.engine.incoming.MessageRouter
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageType.CASH_IN_OUT_OPERATION
import com.swisschain.matching.engine.messages.MessageType.CASH_TRANSFER_OPERATION
import com.swisschain.matching.engine.messages.MessageType.RESERVED_CASH_IN_OUT_OPERATION
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import io.grpc.stub.StreamObserver

class CashApiService(private val messageRouter: MessageRouter): CashServiceGrpc.CashServiceImplBase() {

    override fun cashInOut(request: IncomingMessages.CashInOutOperation?, responseObserver: StreamObserver<IncomingMessages.Response>?) {
        if (request != null) {
            messageRouter.process(GenericMessageWrapper(CASH_IN_OUT_OPERATION.type, request, responseObserver, true))
        }
    }

    override fun cashTransfer(request: IncomingMessages.CashTransferOperation?, responseObserver: StreamObserver<IncomingMessages.Response>?) {
        if (request != null) {
            messageRouter.process(GenericMessageWrapper(CASH_TRANSFER_OPERATION.type, request, responseObserver, true))
        }
    }

    override fun reservedCashInOut(request: IncomingMessages.ReservedCashInOutOperation?, responseObserver: StreamObserver<IncomingMessages.Response>?) {
        if (request != null) {
            messageRouter.process(GenericMessageWrapper(RESERVED_CASH_IN_OUT_OPERATION.type, request, responseObserver, true))
        }
    }
}