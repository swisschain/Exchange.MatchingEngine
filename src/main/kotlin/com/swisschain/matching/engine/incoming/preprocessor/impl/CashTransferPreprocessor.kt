package com.swisschain.matching.engine.incoming.preprocessor.impl

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.daos.context.CashTransferContext
import com.swisschain.matching.engine.database.CashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.common.entity.PersistenceData
import com.swisschain.matching.engine.deduplication.ProcessedMessagesCache
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.incoming.parsers.data.CashTransferParsedData
import com.swisschain.matching.engine.incoming.parsers.impl.CashTransferContextParser
import com.swisschain.matching.engine.incoming.preprocessor.AbstractMessagePreprocessor
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageStatus.DUPLICATE
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.services.validators.input.CashTransferOperationInputValidator
import com.swisschain.matching.engine.utils.NumberUtils
import com.swisschain.matching.engine.utils.order.MessageStatusUtils
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingQueue

@Component
class CashTransferPreprocessor(contextParser: CashTransferContextParser,
                               preProcessedMessageQueue: BlockingQueue<MessageWrapper>,
                               private val cashOperationIdDatabaseAccessor: CashOperationIdDatabaseAccessor,
                               private val cashTransferPreprocessorPersistenceManager: PersistenceManager,
                               private val processedMessagesCache: ProcessedMessagesCache,
                               private val messageProcessingStatusHolder: MessageProcessingStatusHolder,
                               @Qualifier("cashTransferPreProcessingLogger")
                               private val logger: ThrottlingLogger) :
        AbstractMessagePreprocessor<CashTransferParsedData>(contextParser,
                messageProcessingStatusHolder,
                preProcessedMessageQueue,
                logger) {

    companion object {
        private val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    @Autowired
    private lateinit var cashTransferOperationInputValidator: CashTransferOperationInputValidator

    override fun preProcessParsedData(parsedData: CashTransferParsedData): Boolean {
        val parsedMessageWrapper = parsedData.messageWrapper
        val context = parsedMessageWrapper.context as CashTransferContext
        if (messageProcessingStatusHolder.isCashTransferDisabled(context.transferOperation.asset)) {
            writeResponse(parsedMessageWrapper, MessageStatus.MESSAGE_PROCESSING_DISABLED)
            return false
        }

        if (!validateData(parsedData)) {
            return false
        }

        if (isMessageDuplicated(parsedData)) {
            writeResponse(parsedMessageWrapper, DUPLICATE)
            val errorMessage = "Message already processed: ${parsedMessageWrapper.type}: ${context.messageId}"
            logger.info(errorMessage)
            METRICS_LOGGER.logError(errorMessage)
            return false
        }

        return true
    }

    fun validateData(cashTransferParsedData: CashTransferParsedData): Boolean {
        try {
            cashTransferOperationInputValidator.performValidation(cashTransferParsedData)
        } catch (e: ValidationException) {
            processInvalidData(cashTransferParsedData, e.validationType, e.message)
            return false
        }

        return true
    }

    private fun processInvalidData(cashTransferParsedData: CashTransferParsedData,
                                   validationType: ValidationException.Validation,
                                   message: String) {
        val messageWrapper = cashTransferParsedData.messageWrapper
        val context = messageWrapper.context as CashTransferContext
        logger.info("Input validation failed messageId: ${context.messageId}, details: $message")

        val persistSuccess = cashTransferPreprocessorPersistenceManager.persist(PersistenceData(context.processedMessage))
        if (!persistSuccess) {
            throw Exception("Persistence error")
        }

        try {
            processedMessagesCache.addMessage(context.processedMessage)
            writeErrorResponse(messageWrapper, context, MessageStatusUtils.toMessageStatus(validationType), message)
        } catch (e: Exception) {
            logger.error("Error occurred during processing of invalid cash transfer data, context $context", e)
            METRICS_LOGGER.logError("Error occurred during invalid data processing, ${messageWrapper.type} ${context.messageId}")
        }
    }

    private fun isMessageDuplicated(cashTransferParsedData: CashTransferParsedData): Boolean {
        val parsedMessageWrapper = cashTransferParsedData.messageWrapper
        val context = parsedMessageWrapper.context as CashTransferContext
        return cashOperationIdDatabaseAccessor.isAlreadyProcessed(parsedMessageWrapper.type.toString(), context.messageId)
    }

    private fun writeErrorResponse(messageWrapper: MessageWrapper,
                                   context: CashTransferContext,
                                   status: MessageStatus,
                                   errorMessage: String = "") {
        messageWrapper.writeResponse(IncomingMessages.Response.newBuilder()
                .setMatchingEngineId(StringValue.of(context.transferOperation.matchingEngineOperationId))
                .setStatus(IncomingMessages.Status.forNumber(status.type))
                .setStatusReason(StringValue.of(errorMessage)))
        logger.info("Cash transfer operation (${context.transferOperation.externalId}) from client ${context.transferOperation.fromWalletId} " +
                "to client ${context.transferOperation.toWalletId}, asset ${context.transferOperation.asset}," +
                " volume: ${NumberUtils.roundForPrint(context.transferOperation.volume)}: $errorMessage")
    }
}