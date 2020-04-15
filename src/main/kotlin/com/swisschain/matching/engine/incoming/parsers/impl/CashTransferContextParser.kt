package com.swisschain.matching.engine.incoming.parsers.impl

import com.swisschain.matching.engine.daos.TransferOperation
import com.swisschain.matching.engine.daos.context.CashTransferContext
import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.CashTransferParsedData
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.toDate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Date

@Component
class CashTransferContextParser(private val assetsHolder: AssetsHolder,
                                private val uuidHolder: UUIDHolder) : ContextParser<CashTransferParsedData> {
    override fun parse(messageWrapper: MessageWrapper): CashTransferParsedData {

        val message = messageWrapper.parsedMessage as IncomingMessages.CashTransferOperation

        val feeInstructions = NewFeeInstruction.create(message.feesList)

        val transferOperation = TransferOperation(
                uuidHolder.getNextValue(),
                message.brokerId,
                message.id,
                message.fromWalletId,
                message.toWalletId,
                assetsHolder.getAsset(
                        message.brokerId,
                        message.assetId
                ),
                Date(message.timestamp.seconds),
                BigDecimal(message.volume),
                if (message.hasOverdraftLimit()) BigDecimal(message.overdraftLimit.value) else null,
                feeInstructions
        )

        messageWrapper.id = message.id
        messageWrapper.messageId = if (message.hasMessageId()) message.messageId.value else message.id
        messageWrapper.timestamp = message.timestamp.seconds
        messageWrapper.processedMessage = ProcessedMessage(
                MessageType.CASH_TRANSFER_OPERATION.type,
                message.timestamp.toDate().time,
                messageWrapper.messageId!!
        )

        messageWrapper.context =
                CashTransferContext(
                        if (message.hasMessageId()) message.messageId.value else message.id,
                        transferOperation,
                        messageWrapper.processedMessage!!)

        return CashTransferParsedData(messageWrapper, message.assetId, feeInstructions)
    }
}