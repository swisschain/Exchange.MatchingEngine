package com.swisschain.matching.engine.services

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.process.common.CancelRequest
import com.swisschain.matching.engine.order.process.common.LimitOrdersCancelExecutor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LimitOrdersCancelServiceHelper(private val limitOrdersCancelExecutor: LimitOrdersCancelExecutor) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(LimitOrderCancelService::class.java.name)
    }

    fun cancelOrdersAndWriteResponse(cancelRequest: CancelRequest): Boolean {
        val updateSuccessful = limitOrdersCancelExecutor.cancelOrdersAndApply(cancelRequest)

        if (updateSuccessful) {
            writeResponse(cancelRequest.messageWrapper!!, MessageStatus.OK)
        } else {
            val message = "Unable to save result"
            writeResponse(cancelRequest.messageWrapper!!, MessageStatus.RUNTIME, message)
            LOGGER.info("$message for operation ${cancelRequest.messageId}")
        }
        return updateSuccessful
    }

    private fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus) {
        writeResponse(messageWrapper, status, null)
    }

    private fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus, message: String?) {
        val builder = IncomingMessages.Response.newBuilder().setStatus(IncomingMessages.Status.forNumber(status.type))

        message?.let {
            builder.statusReason = StringValue.of(message)
        }

        messageWrapper.writeResponse(builder)
    }
}