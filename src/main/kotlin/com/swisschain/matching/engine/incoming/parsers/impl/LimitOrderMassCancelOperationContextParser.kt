package com.swisschain.matching.engine.incoming.parsers.impl

import com.swisschain.matching.engine.daos.context.LimitOrderMassCancelOperationContext
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderMassCancelOperationParsedData
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import org.springframework.stereotype.Component
import java.util.Date

@Component
class LimitOrderMassCancelOperationContextParser: ContextParser<LimitOrderMassCancelOperationParsedData> {
    override fun parse(messageWrapper: MessageWrapper): LimitOrderMassCancelOperationParsedData {
        messageWrapper.context = parseMessage(messageWrapper)
        return LimitOrderMassCancelOperationParsedData(messageWrapper)
    }

    private fun parseMessage(messageWrapper: MessageWrapper): LimitOrderMassCancelOperationContext {
        val message = messageWrapper.parsedMessage as IncomingMessages.LimitOrderMassCancel

        val messageId = if (message.hasMessageId()) message.messageId.value else message.id
        messageWrapper.messageId = messageId
        messageWrapper.id = message.id
        messageWrapper.timestamp = Date().time
        messageWrapper.processedMessage = ProcessedMessage(messageWrapper.type, messageWrapper.timestamp!!, messageWrapper.messageId!!)

        val messageType =  MessageType.valueOf(messageWrapper.type) ?: throw Exception("Unknown message type ${messageWrapper.type}")

        val walletId = if (message.hasWalletId()) message.walletId.value else null
        val assetPairId = if (message.hasAssetPairId()) message.assetPairId.value else null
        val isBuy = if (message.hasIsBuy()) message.isBuy.value else null

        return LimitOrderMassCancelOperationContext(
                message.brokerId,
                message.id,
                messageId,
                walletId,
                messageWrapper.processedMessage!!,
                messageType,
                assetPairId,
                isBuy
        )
    }
}