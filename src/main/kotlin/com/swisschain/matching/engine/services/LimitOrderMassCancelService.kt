package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.daos.context.LimitOrderMassCancelOperationContext
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.process.common.CancelRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Date

@Service
class LimitOrderMassCancelService(private val genericLimitOrderService: GenericLimitOrderService,
                                  private val genericStopLimitOrderService: GenericStopLimitOrderService,
                                  private val limitOrdersCancelServiceHelper: LimitOrdersCancelServiceHelper) : AbstractService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(LimitOrderMassCancelService::class.java.name)
    }

    override fun processMessage(messageWrapper: MessageWrapper) {
        val now = Date()
        val context = messageWrapper.context as LimitOrderMassCancelOperationContext
        LOGGER.debug("Got mass limit order cancel request id: ${context.uid}, walletId: ${context.walletId}, assetPairId: ${context.assetPairId}, isBuy: ${context.isBuy}")

        limitOrdersCancelServiceHelper.cancelOrdersAndWriteResponse(
                CancelRequest(
                        genericLimitOrderService.searchOrders(context.brokerId, context.walletId, context.assetPairId, context.isBuy),
                        genericStopLimitOrderService.searchOrders(context.brokerId, context.walletId, context.assetPairId, context.isBuy),
                        context.messageId,
                        context.uid,
                        context.messageType,
                        now,
                        context.processedMessage,
                        messageWrapper,
                        LOGGER))
    }

    override fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus) {
        messageWrapper.writeResponse(IncomingMessages.Response.newBuilder().setStatus(IncomingMessages.Status.forNumber(status.type)))
    }
}