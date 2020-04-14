package com.swisschain.matching.engine.incoming.parsers.impl

import com.swisschain.matching.engine.daos.CashInOutOperation
import com.swisschain.matching.engine.daos.context.CashInOutContext
import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.CashInOutParsedData
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.toDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class CashInOutContextParser(@Value("#{Config.me.defaultBroker}" )
                             private val defaultBroker: String,
                             private val assetsHolder: AssetsHolder,
                             private val uuidHolder: UUIDHolder) : ContextParser<CashInOutParsedData> {
    override fun parse(messageWrapper: MessageWrapper): CashInOutParsedData {
        val operationId = uuidHolder.getNextValue()

        val message = messageWrapper.parsedMessage as IncomingMessages.CashInOutOperation

        messageWrapper.id = message.id
        messageWrapper.messageId = if (message.hasMessageId()) message.messageId.value else message.id
        messageWrapper.timestamp = message.timestamp.seconds
        messageWrapper.processedMessage = ProcessedMessage(MessageType.CASH_IN_OUT_OPERATION.type, message.timestamp.seconds, messageWrapper.messageId!!)

        val brokerId = if (!message.brokerId.isNullOrEmpty()) message.brokerId else defaultBroker

        messageWrapper.context = CashInOutContext(
                if (message.hasMessageId()) message.messageId.value else message.id,
                messageWrapper.processedMessage!!,
                CashInOutOperation(operationId, message.id, brokerId,
                        message.walletId, assetsHolder.getAssetAllowNulls(brokerId, message.assetId),
                        message.timestamp.toDate(), BigDecimal(message.volume),
                        feeInstructions = NewFeeInstruction.create(message.feesList)))

        return CashInOutParsedData(messageWrapper, message.assetId)
    }
}