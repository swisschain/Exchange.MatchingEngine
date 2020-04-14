package com.swisschain.matching.engine.incoming.preprocessor.impl

import com.swisschain.matching.engine.daos.context.SingleLimitOrderContext
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.incoming.parsers.data.SingleLimitOrderParsedData
import com.swisschain.matching.engine.incoming.parsers.impl.SingleLimitOrderContextParser
import com.swisschain.matching.engine.incoming.preprocessor.AbstractMessagePreprocessor
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.services.validators.impl.OrderValidationException
import com.swisschain.matching.engine.services.validators.impl.OrderValidationResult
import com.swisschain.matching.engine.services.validators.input.LimitOrderInputValidator
import com.swisschain.matching.engine.utils.order.MessageStatusUtils
import com.swisschain.utils.logging.ThrottlingLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingQueue

@Component
class SingleLimitOrderPreprocessor(singleLimitOrderContextParser: SingleLimitOrderContextParser,
                                   preProcessedMessageQueue: BlockingQueue<MessageWrapper>,
                                   private val messageProcessingStatusHolder: MessageProcessingStatusHolder,
                                   @Qualifier("singleLimitOrderPreProcessingLogger")
                                   private val logger: ThrottlingLogger) :
        AbstractMessagePreprocessor<SingleLimitOrderParsedData>(singleLimitOrderContextParser,
                messageProcessingStatusHolder,
                preProcessedMessageQueue,
                logger) {

    @Autowired
    private lateinit var limitOrderInputValidator: LimitOrderInputValidator

    override fun preProcessParsedData(parsedData: SingleLimitOrderParsedData): Boolean {
        val singleLimitContext = parsedData.messageWrapper.context as SingleLimitOrderContext

        if (messageProcessingStatusHolder.isTradeDisabled(singleLimitContext.assetPair)) {
            writeResponse(parsedData.messageWrapper, MessageStatus.MESSAGE_PROCESSING_DISABLED)
            return false
        }

        val validationResult = getValidationResult(parsedData)

        //currently if order is not valid at all - can not be passed to the business thread - ignore it
        if (validationResult.isFatalInvalid) {
            logger.error("Fatal validation error occurred, ${validationResult.message} " +
                    "Error details: $singleLimitContext")
            writeResponse(parsedData.messageWrapper, MessageStatusUtils.toMessageStatus(validationResult.status!!), validationResult.message)
            return false
        }

        singleLimitContext.validationResult = validationResult
        return true
    }

    private fun getValidationResult(singleLimitOrderParsedData: SingleLimitOrderParsedData): OrderValidationResult {
        val singleLimitContext = singleLimitOrderParsedData.messageWrapper.context as SingleLimitOrderContext

        try {
            when (singleLimitContext.limitOrder.type) {
                LimitOrderType.LIMIT -> limitOrderInputValidator.validateLimitOrder(singleLimitOrderParsedData)
                LimitOrderType.STOP_LIMIT -> limitOrderInputValidator.validateStopOrder(singleLimitOrderParsedData)
            }
        } catch (e: OrderValidationException) {
            return OrderValidationResult(false, isFatalInvalid(e), e.message, e.orderStatus)
        }

        return OrderValidationResult(true)
    }

    private fun isFatalInvalid(validationException: OrderValidationException): Boolean {
        return validationException.orderStatus == OrderStatus.UnknownAsset
    }
}