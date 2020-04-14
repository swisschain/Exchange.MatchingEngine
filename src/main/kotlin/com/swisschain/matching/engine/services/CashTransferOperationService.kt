package com.swisschain.matching.engine.services

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.balance.BalanceException
import com.swisschain.matching.engine.daos.TransferOperation
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.daos.context.CashTransferContext
import com.swisschain.matching.engine.exception.PersistenceException
import com.swisschain.matching.engine.fee.FeeException
import com.swisschain.matching.engine.fee.FeeProcessor
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageStatus.INVALID_FEE
import com.swisschain.matching.engine.messages.MessageStatus.LOW_BALANCE
import com.swisschain.matching.engine.messages.MessageStatus.OK
import com.swisschain.matching.engine.messages.MessageStatus.RUNTIME
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.CashTransferEventData
import com.swisschain.matching.engine.outgoing.senders.OutgoingEventProcessor
import com.swisschain.matching.engine.services.validators.business.CashTransferOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.utils.NumberUtils
import com.swisschain.matching.engine.utils.order.MessageStatusUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.LinkedList
import java.util.concurrent.BlockingQueue

@Service
class CashTransferOperationService(private val balancesHolder: BalancesHolder,
                                   private val dbTransferOperationQueue: BlockingQueue<TransferOperation>,
                                   private val feeProcessor: FeeProcessor,
                                   private val cashTransferOperationBusinessValidator: CashTransferOperationBusinessValidator,
                                   private val messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                   private val outgoingEventProcessor: OutgoingEventProcessor,
                                   @Value("#{Config.me.defaultBroker}" )
                                   private val defaultBrokerId: String) : AbstractService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CashTransferOperationService::class.java.name)
    }

    override fun processMessage(messageWrapper: MessageWrapper) {
        val now = Date()
        val cashTransferContext = messageWrapper.context as CashTransferContext

        val transferOperation = cashTransferContext.transferOperation

        val brokerId = if (transferOperation.brokerId.isNotEmpty()) transferOperation.brokerId else defaultBrokerId

        val asset = transferOperation.asset
        LOGGER.debug("Processing cash transfer operation ${transferOperation.externalId}) messageId: ${cashTransferContext.messageId}" +
                " from client ${transferOperation.fromWalletId} to client ${transferOperation.toWalletId}, " +
                "asset $asset, volume: ${NumberUtils.roundForPrint(transferOperation.volume)}, " +
                "feeInstructions: ${transferOperation.fees}")

        try {
            cashTransferOperationBusinessValidator.performValidation(cashTransferContext)
        } catch (e: ValidationException) {
            writeErrorResponse(messageWrapper, cashTransferContext, MessageStatusUtils.toMessageStatus(e.validationType), e.message)
            return
        }

        try {
            processTransferOperation(brokerId, transferOperation, messageWrapper, cashTransferContext, now)
        } catch (e: FeeException) {
            writeErrorResponse(messageWrapper, cashTransferContext, INVALID_FEE, e.message)
            return
        } catch (e: BalanceException) {
            writeErrorResponse(messageWrapper, cashTransferContext, LOW_BALANCE, e.message)
            return
        } catch (e: PersistenceException) {
            writeErrorResponse(messageWrapper, cashTransferContext, RUNTIME, e.message)
            return
        }
        dbTransferOperationQueue.put(transferOperation)

        writeResponse(messageWrapper, transferOperation.matchingEngineOperationId, OK)
        LOGGER.info("Cash transfer operation (${transferOperation.externalId}) from client ${transferOperation.fromWalletId} to client ${transferOperation.toWalletId}," +
                " asset $asset, volume: ${NumberUtils.roundForPrint(transferOperation.volume)} processed")
    }

    private fun processTransferOperation(brokerId: String,
                                         operation: TransferOperation,
                                         messageWrapper: MessageWrapper,
                                         cashTransferContext: CashTransferContext,
                                         now: Date) {
        val operations = LinkedList<WalletOperation>()

        val assetId = operation.asset.assetId
        operations.add(WalletOperation(brokerId, operation.fromWalletId, assetId, -operation.volume))
        val receiptOperation = WalletOperation(brokerId, operation.toWalletId, assetId, operation.volume)
        operations.add(receiptOperation)

        val fees = feeProcessor.processFee(brokerId, operation.fees, receiptOperation, operations, balancesGetter = balancesHolder)

        val walletProcessor = balancesHolder.createWalletProcessor(LOGGER)
                .preProcess(operations, true)

        val sequenceNumber = messageSequenceNumberHolder.getNewValue()
        val updated = walletProcessor.persistBalances(cashTransferContext.processedMessage, null, null, sequenceNumber)
        messageWrapper.triedToPersist = true
        messageWrapper.persisted = updated
        if (!updated) {
            throw PersistenceException("Unable to save balance")
        }
        walletProcessor.apply()
        outgoingEventProcessor.submitCashTransferEvent(CashTransferEventData(cashTransferContext.messageId,
                walletProcessor,
                fees,
                operation,
                sequenceNumber,
                now))
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
                                   context: CashTransferContext,
                                   status: MessageStatus,
                                   errorMessage: String = "") {
        messageWrapper.writeResponse(IncomingMessages.Response.newBuilder()
                .setMatchingEngineId(StringValue.of(context.transferOperation.matchingEngineOperationId))
                .setStatus(IncomingMessages.Status.forNumber(status.type))
                .setStatusReason(StringValue.of(errorMessage)))
        LOGGER.info("Cash transfer operation (${context.transferOperation.externalId}) from client ${context.transferOperation.fromWalletId} " +
                "to client ${context.transferOperation.toWalletId}, asset ${context.transferOperation.asset}," +
                " volume: ${NumberUtils.roundForPrint(context.transferOperation.volume)}: $errorMessage")
    }
}