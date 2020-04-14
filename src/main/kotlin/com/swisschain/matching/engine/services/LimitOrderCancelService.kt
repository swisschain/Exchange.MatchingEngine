package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.context.LimitOrderCancelOperationContext
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.process.common.CancelRequest
import com.swisschain.matching.engine.services.validators.business.LimitOrderCancelOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.utils.order.MessageStatusUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Date
import java.util.stream.Collectors

@Service
class LimitOrderCancelService(private val genericLimitOrderService: GenericLimitOrderService,
                              private val genericStopLimitOrderService: GenericStopLimitOrderService,
                              private val validator: LimitOrderCancelOperationBusinessValidator,
                              private val limitOrdersCancelServiceHelper: LimitOrdersCancelServiceHelper) : AbstractService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(LimitOrderCancelService::class.java.name)
    }

    override fun processMessage(messageWrapper: MessageWrapper) {
        val now = Date()
        val context = messageWrapper.context as LimitOrderCancelOperationContext


        LOGGER.debug("Got limit order cancel request (messageId: ${context.messageId}, id: ${context.uid}, orders: ${context.limitOrderIds})")
        val ordersByType = getLimitOrderTypeToLimitOrders(context.brokerId, context.limitOrderIds)

        try {
            validator.performValidation(ordersByType, context)
        } catch (e: ValidationException) {
            LOGGER.info("Business validation failed: ${context.messageId}, details: ${e.message}")
            writeResponse(messageWrapper, MessageStatusUtils.toMessageStatus(e.validationType))
            return
        }

        limitOrdersCancelServiceHelper.cancelOrdersAndWriteResponse(CancelRequest(ordersByType[LimitOrderType.LIMIT] ?: emptyList(),
                ordersByType[LimitOrderType.STOP_LIMIT] ?: emptyList(),
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

    private fun getLimitOrderTypeToLimitOrders(brokerId: String, orderIds: Set<String>): Map<LimitOrderType, List<LimitOrder>> {
        return orderIds.stream()
                .map { getOrder(brokerId, it) }
                .filter { limitOrder -> limitOrder != null }
                .map { t -> t!! }
                .collect(Collectors.groupingBy { limitOrder: LimitOrder -> limitOrder.type ?: LimitOrderType.LIMIT })
    }

    private fun getOrder(brokerId: String, orderId: String): LimitOrder? {
        return genericLimitOrderService.getOrder(brokerId, orderId) ?: genericStopLimitOrderService.getOrder(brokerId, orderId)
    }
}
