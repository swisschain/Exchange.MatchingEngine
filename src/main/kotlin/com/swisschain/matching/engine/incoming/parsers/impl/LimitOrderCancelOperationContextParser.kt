package com.swisschain.matching.engine.incoming.parsers.impl

import com.swisschain.matching.engine.daos.context.LimitOrderCancelOperationContext
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderCancelOperationParsedData
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import org.springframework.stereotype.Component
import java.util.Date

@Component
class LimitOrderCancelOperationContextParser: ContextParser<LimitOrderCancelOperationParsedData> {
    override fun parse(messageWrapper: MessageWrapper): LimitOrderCancelOperationParsedData {
        messageWrapper.context = parseContext(messageWrapper)
        return LimitOrderCancelOperationParsedData(messageWrapper)
    }

    private fun parseContext(messageWrapper: MessageWrapper): LimitOrderCancelOperationContext {
        val message = messageWrapper.parsedMessage as IncomingMessages.LimitOrderCancel
        messageWrapper.messageId = if (message.hasMessageId()) message.messageId.value else message.uid.toString()
        messageWrapper.timestamp = Date().time
        messageWrapper.id = message.uid
        messageWrapper.processedMessage = ProcessedMessage(messageWrapper.type, messageWrapper.timestamp!!, messageWrapper.messageId!!)

        return LimitOrderCancelOperationContext(
                message.brokerId,
                message.uid,
                messageWrapper.messageId!!,
                messageWrapper.processedMessage!!,
                message.limitOrderIdList.toSet(),
                getMessageType(messageWrapper.type)
        )
    }

    private fun getMessageType(type: Byte): MessageType {
        return MessageType.valueOf(type) ?: throw Exception("Unknown message type $type")
    }
}