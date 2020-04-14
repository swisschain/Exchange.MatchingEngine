package com.swisschain.matching.engine.services

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.balance.BalanceException
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.ReservedCashInOutEventData
import com.swisschain.matching.engine.outgoing.senders.OutgoingEventProcessor
import com.swisschain.matching.engine.services.validators.ReservedCashInOutOperationValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.utils.order.MessageStatusUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Date

@Service
class ReservedCashInOutOperationService @Autowired constructor(private val assetsHolder: AssetsHolder,
                                                               private val balancesHolder: BalancesHolder,
                                                               private val reservedCashInOutOperationValidator: ReservedCashInOutOperationValidator,
                                                               private val messageProcessingStatusHolder: MessageProcessingStatusHolder,
                                                               private val uuidHolder: UUIDHolder,
                                                               private val messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                                               private val outgoingEventProcessor: OutgoingEventProcessor,
                                                               @Value("#{Config.me.defaultBroker}" )
                                                               private val defaultBrokerId: String) : AbstractService {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ReservedCashInOutOperationService::class.java.name)
    }

    override fun processMessage(messageWrapper: MessageWrapper) {
        val message = messageWrapper.parsedMessage!! as IncomingMessages.ReservedCashInOutOperation
        val brokerId = if (!message.brokerId.isNullOrEmpty()) message.brokerId else defaultBrokerId
        val asset = assetsHolder.getAsset(brokerId, message.assetId)
        if ((isCashIn(message.reservedVolume) && messageProcessingStatusHolder.isCashInDisabled(asset)) ||
                (!isCashIn(message.reservedVolume) && messageProcessingStatusHolder.isCashOutDisabled(asset))) {
            writeResponse(messageWrapper, MessageStatus.MESSAGE_PROCESSING_DISABLED)
            return
        }

        LOGGER.debug("Processing reserved cash in/out messageId: ${messageWrapper.messageId} " +
                "operation (${message.id}) for client ${message.walletId}, " +
                "asset ${message.assetId}, amount: ${message.reservedVolume}")

        val now = Date()
        val matchingEngineOperationId = uuidHolder.getNextValue()
        val operation = WalletOperation(brokerId, message.walletId, message.assetId, BigDecimal.ZERO, BigDecimal(message.reservedVolume))

        try {
            reservedCashInOutOperationValidator.performValidation(message)
        } catch (e: ValidationException) {
            writeErrorResponse(messageWrapper, matchingEngineOperationId, MessageStatusUtils.toMessageStatus(e.validationType), e.message)
            return
        }

        val walletProcessor = balancesHolder.createWalletProcessor(LOGGER)
        try {
            walletProcessor.preProcess(listOf(operation), allowTrustedClientReservedBalanceOperation = true)
        } catch (e: BalanceException) {
            LOGGER.info("Reserved cash in/out operation (${message.id}) failed due to invalid balance: ${e.message}")
            writeErrorResponse(messageWrapper, matchingEngineOperationId, MessageStatus.LOW_BALANCE, e.message)
            return
        }

        val sequenceNumber = messageSequenceNumberHolder.getNewValue()
        val updated = walletProcessor.persistBalances(messageWrapper.processedMessage, null, null, sequenceNumber)
        messageWrapper.triedToPersist = true
        messageWrapper.persisted = updated
        if (!updated) {
            writeErrorResponse(messageWrapper, matchingEngineOperationId, MessageStatus.RUNTIME)
            LOGGER.info("Reserved cash in/out operation (${message.id}) for client ${message.walletId} asset ${message.assetId}, volume: ${message.reservedVolume}: unable to save balance")
            return
        }

        walletProcessor.apply()

        outgoingEventProcessor.submitReservedCashInOutEvent(ReservedCashInOutEventData(sequenceNumber,
                messageWrapper.messageId!!,
                message.id,
                now,
                walletProcessor.getClientBalanceUpdates(),
                operation,
                walletProcessor,
                asset.accuracy))

        writeResponse(messageWrapper, matchingEngineOperationId, MessageStatus.OK)

        LOGGER.info("Reserved cash in/out operation (${message.id}) for client ${message.walletId}, " +
                "asset ${message.assetId}, amount: ${message.reservedVolume} processed")
    }

    fun writeResponse(messageWrapper: MessageWrapper, matchingEngineOperationId: String, status: MessageStatus) {
        messageWrapper.writeResponse(IncomingMessages.Response.newBuilder()
                .setMatchingEngineId(StringValue.of(matchingEngineOperationId))
                .setStatus(IncomingMessages.Status.forNumber(status.type))
        )
    }


    override fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus) {
        messageWrapper.writeResponse(IncomingMessages.Response.newBuilder()
                .setStatus(IncomingMessages.Status.forNumber(status.type))
        )
    }

    fun writeErrorResponse(messageWrapper: MessageWrapper, matchingEngineOperationId: String, status: MessageStatus, errorMessage: String = "") {
        messageWrapper.writeResponse(IncomingMessages.Response
                .newBuilder()
                .setMatchingEngineId(StringValue.of(matchingEngineOperationId))
                .setStatus(IncomingMessages.Status.forNumber(status.type))
                .setStatusReason(StringValue.of(errorMessage)))
    }

    private fun isCashIn(amount: String): Boolean {
        return BigDecimal(amount) > BigDecimal.ZERO
    }
}