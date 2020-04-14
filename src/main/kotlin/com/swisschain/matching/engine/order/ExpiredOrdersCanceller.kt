package com.swisschain.matching.engine.order

import com.swisschain.matching.engine.daos.context.LimitOrderCancelOperationContext
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.incoming.MessageRouter
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

@Component
class ExpiredOrdersCanceller(private val expiryOrdersQueue: ExpiryOrdersQueue,
                             private val messageRouter: MessageRouter) {

    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(ExpiredOrdersCanceller::class.java.name)
        private val METRICS_LOGGER = MetricsLogger.getLogger()
        private val MESSAGE_TYPE = MessageType.LIMIT_ORDER_CANCEL
    }

    @Scheduled(fixedRateString = "\${expired.orders.cancel.interval}")
    fun cancelExpiredOrders() {
        try {
            val now = Date()
            val ordersExternalIdsToCancel = expiryOrdersQueue.getExpiredOrdersExternalIds(now)

            ordersExternalIdsToCancel.forEach {
                if (it.value.isEmpty()) {
                    return
                }
                val messageId = UUID.randomUUID().toString()
                val requestId = UUID.randomUUID().toString()
                LOGGER.info("Generating message to cancel expired orders: brokerId=${it.key}, messageId=$messageId, requestId=$requestId, date=$now, orders=$ordersExternalIdsToCancel")

                val messageWrapper = createMessageWrapper(it.key,
                        messageId,
                        requestId,
                        now,
                        it.value)

                messageRouter.preProcessedMessageQueue.put(messageWrapper)
            }
        } catch (e: Exception) {
            val message = "Unable to cancel expired orders"
            LOGGER.error(message, e)
            METRICS_LOGGER.logError(message, e)
        }
    }

    private fun createMessageWrapper(brokerId: String,
                                     messageId: String,
                                     requestId: String,
                                     date: Date,
                                     ordersExternalIds: Collection<String>): MessageWrapper {
        return GenericMessageWrapper(MESSAGE_TYPE.type,
                null,
                null,
                false,
                messageId,
                requestId,
                createOperationContext(brokerId,
                        messageId,
                        requestId,
                        date,
                        ordersExternalIds))
    }

    private fun createOperationContext(brokerId: String,
                                       messageId: String,
                                       requestId: String,
                                       date: Date,
                                       ordersExternalIds: Collection<String>): LimitOrderCancelOperationContext {
        return LimitOrderCancelOperationContext(brokerId,
                requestId,
                messageId,
                createProcessedMessage(messageId, date),
                ordersExternalIds.toSet(),
                MESSAGE_TYPE)
    }

    private fun createProcessedMessage(messageId: String,
                                       date: Date): ProcessedMessage {
        return ProcessedMessage(MESSAGE_TYPE.type,
                date.time,
                messageId)
    }
}