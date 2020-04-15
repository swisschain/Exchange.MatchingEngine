package com.swisschain.matching.engine.services

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.balance.BalanceException
import com.swisschain.matching.engine.daos.context.CashInOutContext
import com.swisschain.matching.engine.daos.converters.CashInOutOperationConverter
import com.swisschain.matching.engine.fee.FeeException
import com.swisschain.matching.engine.fee.FeeProcessor
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageStatus.INVALID_FEE
import com.swisschain.matching.engine.messages.MessageStatus.OK
import com.swisschain.matching.engine.messages.MessageStatus.RUNTIME
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.CashInOutEventData
import com.swisschain.matching.engine.outgoing.senders.OutgoingEventProcessor
import com.swisschain.matching.engine.services.validators.business.CashInOutOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.utils.NumberUtils
import com.swisschain.matching.engine.utils.order.MessageStatusUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Date

@Service
class CashInOutOperationService(private val balancesHolder: BalancesHolder,
                                private val feeProcessor: FeeProcessor,
                                private val cashInOutOperationBusinessValidator: CashInOutOperationBusinessValidator,
                                private val messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                private val outgoingEventProcessor: OutgoingEventProcessor) : AbstractService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CashInOutOperationService::class.java.name)
    }

    override fun processMessage(messageWrapper: MessageWrapper) {
        val now = Date()
        val cashInOutContext: CashInOutContext = messageWrapper.context as CashInOutContext
        val cashInOutOperation = cashInOutContext.cashInOutOperation
        val feeInstructions = cashInOutOperation.feeInstructions
        val walletOperation = CashInOutOperationConverter.fromCashInOutOperationToWalletOperation(cashInOutOperation)

        val asset = cashInOutOperation.asset!!
        LOGGER.debug("Processing cash in/out messageId: ${cashInOutContext.messageId} operation (${cashInOutOperation.externalId})" +
                " for client ${cashInOutContext.cashInOutOperation.walletId}, asset ${asset.assetId}," +
                " amount: ${NumberUtils.roundForPrint(walletOperation.amount)}, feeInstructions: $feeInstructions")


        val operations = mutableListOf(walletOperation)

        try {
            cashInOutOperationBusinessValidator.performValidation(cashInOutContext)
        } catch (e: ValidationException) {
            writeErrorResponse(messageWrapper, cashInOutOperation.matchingEngineOperationId, MessageStatusUtils.toMessageStatus(e.validationType), e.message)
            return
        }

        val fees = try {
            feeProcessor.processFee(
                    cashInOutOperation.brokerId,
                    feeInstructions,
                    walletOperation,
                    operations,
                    balancesGetter = balancesHolder
            )
        } catch (e: FeeException) {
            writeErrorResponse(messageWrapper, cashInOutOperation.matchingEngineOperationId, INVALID_FEE, e.message)
            return
        }

        val walletProcessor = balancesHolder.createWalletProcessor(LOGGER)
        try {
            walletProcessor.preProcess(operations)
        } catch (e: BalanceException) {
            writeErrorResponse(messageWrapper, cashInOutOperation.matchingEngineOperationId, MessageStatus.LOW_BALANCE, e.message)
            return
        }

        val sequenceNumber = messageSequenceNumberHolder.getNewValue()
        val updated = walletProcessor.persistBalances(cashInOutContext.processedMessage, null, null, sequenceNumber)
        messageWrapper.triedToPersist = true
        messageWrapper.persisted = updated
        if (!updated) {
            writeErrorResponse(messageWrapper, cashInOutOperation.matchingEngineOperationId, RUNTIME, "unable to save balance")
            return
        }

        walletProcessor.apply()

        outgoingEventProcessor.submitCashInOutEvent(CashInOutEventData(messageWrapper.messageId!!,
                cashInOutOperation.externalId!!,
                sequenceNumber,
                now,
                cashInOutOperation.dateTime,
                walletProcessor,
                walletOperation,
                asset,
                fees))

        writeResponse(messageWrapper, cashInOutOperation.matchingEngineOperationId, OK)


        LOGGER.info("Cash in/out walletOperation (${cashInOutOperation.externalId}) for client ${cashInOutContext.cashInOutOperation.walletId}, " +
                "asset ${cashInOutOperation.asset.assetId}, " +
                "amount: ${NumberUtils.roundForPrint(walletOperation.amount)} processed")
    }

    fun writeResponse(messageWrapper: MessageWrapper, matchingEngineOperationId: String, status: MessageStatus) {
        messageWrapper.writeResponse(IncomingMessages.Response.newBuilder()
                .setMatchingEngineId(StringValue.of(matchingEngineOperationId))
                .setStatus(IncomingMessages.Status.forNumber(status.type)))
    }

    override fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus) {
        messageWrapper.writeResponse(IncomingMessages.Response.newBuilder()
                .setStatus(IncomingMessages.Status.forNumber(status.type)))
    }

    private fun writeErrorResponse(messageWrapper: MessageWrapper,
                                   matchingEngineOperationId: String,
                                   status: MessageStatus,
                                   errorMessage: String = "") {
        val context = messageWrapper.context as CashInOutContext
        messageWrapper.writeResponse(IncomingMessages.Response.newBuilder()
                .setMatchingEngineId(StringValue.of(matchingEngineOperationId))
                .setStatus(IncomingMessages.Status.forNumber(status.type))
                .setStatusReason(StringValue.of(errorMessage)))
        LOGGER.info("Cash in/out operation (${context.cashInOutOperation.externalId}), messageId: ${messageWrapper.messageId} for client ${context.cashInOutOperation.walletId}, " +
                "asset ${context.cashInOutOperation.asset!!.assetId}, amount: ${NumberUtils.roundForPrint(context.cashInOutOperation.amount)}: $errorMessage")
    }
}