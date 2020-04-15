package com.swisschain.matching.engine.services

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.balance.BalanceException
import com.swisschain.matching.engine.daos.MarketOrder
import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.matching.MatchingEngine
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.ExecutionDataApplyService
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.order.OrderStatus.InvalidFee
import com.swisschain.matching.engine.order.OrderStatus.InvalidValue
import com.swisschain.matching.engine.order.OrderStatus.InvalidVolume
import com.swisschain.matching.engine.order.OrderStatus.InvalidVolumeAccuracy
import com.swisschain.matching.engine.order.OrderStatus.LeadToNegativeSpread
import com.swisschain.matching.engine.order.OrderStatus.Matched
import com.swisschain.matching.engine.order.OrderStatus.NoLiquidity
import com.swisschain.matching.engine.order.OrderStatus.NotEnoughFunds
import com.swisschain.matching.engine.order.OrderStatus.Processing
import com.swisschain.matching.engine.order.OrderStatus.ReservedVolumeGreaterThanBalance
import com.swisschain.matching.engine.order.OrderStatus.TooHighPriceDeviation
import com.swisschain.matching.engine.order.process.StopOrderBookProcessor
import com.swisschain.matching.engine.order.process.common.MatchingResultHandlingHelper
import com.swisschain.matching.engine.order.process.context.MarketOrderExecutionContext
import com.swisschain.matching.engine.order.transaction.ExecutionContextFactory
import com.swisschain.matching.engine.outgoing.messages.MarketOrderWithTrades
import com.swisschain.matching.engine.outgoing.messages.v2.builders.EventFactory
import com.swisschain.matching.engine.outgoing.messages.v2.toDate
import com.swisschain.matching.engine.services.validators.MarketOrderValidator
import com.swisschain.matching.engine.services.validators.impl.OrderValidationException
import com.swisschain.matching.engine.utils.PrintUtils
import com.swisschain.matching.engine.utils.order.MessageStatusUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Date

@Service
class MarketOrderService @Autowired constructor(
        private val matchingEngine: MatchingEngine,
        private val executionContextFactory: ExecutionContextFactory,
        private val stopOrderBookProcessor: StopOrderBookProcessor,
        private val executionDataApplyService: ExecutionDataApplyService,
        private val matchingResultHandlingHelper: MatchingResultHandlingHelper,
        private val genericLimitOrderService: GenericLimitOrderService,
        private val messageSequenceNumberHolder: MessageSequenceNumberHolder,
        private val messageSender: MessageSender,
        private val assetsPairsHolder: AssetsPairsHolder,
        private val marketOrderValidator: MarketOrderValidator,
        private val applicationSettingsHolder: ApplicationSettingsHolder,
        private val messageProcessingStatusHolder: MessageProcessingStatusHolder,
        private val uuidHolder: UUIDHolder) : AbstractService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MarketOrderService::class.java.name)
        private val STATS_LOGGER = LoggerFactory.getLogger("${MarketOrderService::class.java.name}.stats")
    }

    private var messagesCount: Long = 0
    private var logCount = 100
    private var totalTime: Double = 0.0

    override fun processMessage(messageWrapper: MessageWrapper) {
        val startTime = System.nanoTime()
        parseMessage(messageWrapper)
        val parsedMessage = messageWrapper.parsedMessage!! as IncomingMessages.MarketOrder

        val assetPair = assetsPairsHolder.getAssetPairAllowNulls(parsedMessage.brokerId, parsedMessage.assetPairId)

        val now = Date()
        val feeInstructions: List<NewFeeInstruction>?

        if (messageProcessingStatusHolder.isTradeDisabled(assetPair)) {
            writeResponse(messageWrapper, MessageStatus.MESSAGE_PROCESSING_DISABLED)
            return
        }

        feeInstructions = NewFeeInstruction.create(parsedMessage.feesList)
        LOGGER.debug("Got market order messageId: ${messageWrapper.messageId}, " +
                "id: ${parsedMessage.uid}, client: ${parsedMessage.walletId}, " +
                "asset: ${parsedMessage.assetPairId}, volume: ${parsedMessage.volume}, " +
                "fees: $feeInstructions")

        val order = MarketOrder(
                uuidHolder.getNextValue(),
                parsedMessage.uid,
                parsedMessage.assetPairId,
                parsedMessage.brokerId,
                parsedMessage.walletId,
                BigDecimal(parsedMessage.volume),
                null,
                Processing.name,
                now,
                if (parsedMessage.hasTimestamp()) parsedMessage.timestamp.toDate() else now,
                now,
                null,
                true,
                null,
                feeInstructions
        )

        try {
            marketOrderValidator.performValidation(order, getOrderBook(order), feeInstructions)
        } catch (e: OrderValidationException) {
            order.updateStatus(e.orderStatus, now)
            sendErrorNotification(messageWrapper, order, now)
            writeErrorResponse(messageWrapper, order, e.message)
            return
        }

        val executionContext = executionContextFactory.create(messageWrapper.messageId!!,
                messageWrapper.id!!,
                MessageType.MARKET_ORDER,
                messageWrapper.processedMessage,
                mapOf(Pair(assetPair!!.symbol, assetPair)),
                now,
                LOGGER)

        val marketOrderExecutionContext = MarketOrderExecutionContext(order, executionContext)

        val matchingResult = matchingEngine.match(order,
                getOrderBook(order),
                messageWrapper.messageId!!,
                priceDeviationThreshold = if (assetPair.marketOrderPriceDeviationThreshold >= BigDecimal.ZERO) assetPair.marketOrderPriceDeviationThreshold else applicationSettingsHolder.marketOrderPriceDeviationThreshold(assetPair.symbol),
                executionContext = executionContext)
        marketOrderExecutionContext.matchingResult = matchingResult

        when (OrderStatus.valueOf(matchingResult.orderCopy.status)) {
            ReservedVolumeGreaterThanBalance,
            NoLiquidity,
            LeadToNegativeSpread,
            NotEnoughFunds,
            InvalidFee,
            InvalidVolumeAccuracy,
            InvalidVolume,
            InvalidValue,
            TooHighPriceDeviation -> {
                if (matchingResult.cancelledLimitOrders.isNotEmpty()) {
                    matchingResultHandlingHelper.preProcessCancelledOppositeOrders(marketOrderExecutionContext)
                    matchingResultHandlingHelper.preProcessCancelledOrdersWalletOperations(marketOrderExecutionContext)
                    matchingResultHandlingHelper.processCancelledOppositeOrders(marketOrderExecutionContext)
                    val orderBook = marketOrderExecutionContext.executionContext.orderBooksHolder
                            .getChangedOrderBookCopy(order.brokerId, marketOrderExecutionContext.order.assetPairId)
                    matchingResult.cancelledLimitOrders.forEach {
                        orderBook.removeOrder(it.origin!!)
                    }
                }
                marketOrderExecutionContext.executionContext.marketOrderWithTrades = MarketOrderWithTrades(executionContext.messageId, order)
            }
            Matched -> {
                if (matchingResult.cancelledLimitOrders.isNotEmpty()) {
                    matchingResultHandlingHelper.preProcessCancelledOppositeOrders(marketOrderExecutionContext)
                }
                if (matchingResult.uncompletedLimitOrderCopy != null) {
                    matchingResultHandlingHelper.preProcessUncompletedOppositeOrder(marketOrderExecutionContext)
                }
                marketOrderExecutionContext.ownWalletOperations = matchingResult.ownCashMovements
                val preProcessResult = try {
                    matchingResultHandlingHelper.processWalletOperations(marketOrderExecutionContext)
                    true
                } catch (e: BalanceException) {
                    order.updateStatus(NotEnoughFunds, now)
                    marketOrderExecutionContext.executionContext.marketOrderWithTrades = MarketOrderWithTrades(messageWrapper.messageId!!, order)
                    LOGGER.error("$order: Unable to process wallet operations after matching: ${e.message}")
                    false
                }

                if (preProcessResult) {
                    matchingResult.apply()
                    executionContext.orderBooksHolder.addCompletedOrders(matchingResult.completedLimitOrders.map { it.origin!! })

                    if (matchingResult.cancelledLimitOrders.isNotEmpty()) {
                        matchingResultHandlingHelper.processCancelledOppositeOrders(marketOrderExecutionContext)
                    }
                    if (matchingResult.uncompletedLimitOrderCopy != null) {
                        matchingResultHandlingHelper.processUncompletedOppositeOrder(marketOrderExecutionContext)
                    }

                    matchingResult.skipLimitOrders.forEach { matchingResult.orderBook.put(it) }

                    marketOrderExecutionContext.executionContext.orderBooksHolder
                            .getChangedOrderBookCopy(order.brokerId, order.assetPairId)
                            .setOrderBook(!order.isBuySide(), matchingResult.orderBook)

                    marketOrderExecutionContext.executionContext.marketOrderWithTrades = MarketOrderWithTrades(messageWrapper.messageId!!, order, matchingResult.marketOrderTrades)
                    matchingResult.limitOrdersReport?.orders?.let { marketOrderExecutionContext.executionContext.addClientsLimitOrdersWithTrades(it) }
                }
            }
            else -> {
                executionContext.error("Not handled order status: ${matchingResult.orderCopy.status}")
            }
        }

        stopOrderBookProcessor.checkAndExecuteStopLimitOrders(executionContext)
        val persisted = executionDataApplyService.persistAndSendEvents(messageWrapper, executionContext)
        if (!persisted) {
            writePersistenceErrorResponse(messageWrapper, order)
            return
        }

        writeResponse(messageWrapper, order, MessageStatusUtils.toMessageStatus(order.status))

        val endTime = System.nanoTime()

        messagesCount++
        totalTime += (endTime - startTime).toDouble() / logCount

        if (messagesCount % logCount == 0L) {
            STATS_LOGGER.info("Total: ${PrintUtils.convertToString(totalTime)}. ")
            totalTime = 0.0
        }
    }

    private fun getOrderBook(order: MarketOrder) = genericLimitOrderService.getOrderBook(order.brokerId, order.assetPairId).getOrderBook(!order.isBuySide())

    private fun writePersistenceErrorResponse(messageWrapper: MessageWrapper, order: MarketOrder) {
        val message = "Unable to save result data"
        LOGGER.error("$order: $message")
        writeResponse(messageWrapper, order, MessageStatus.RUNTIME, message)
        return
    }

    private fun writeResponse(messageWrapper: MessageWrapper, order: MarketOrder, status: MessageStatus, reason: String? = null) {
        val marketOrderResponse = IncomingMessages.MarketOrderResponse.newBuilder()
                .setStatus(IncomingMessages.Status.forNumber(status.type))
        if (order.price != null) {
            marketOrderResponse.price = StringValue.of(order.price!!.toPlainString())
        } else if (reason != null) {
            marketOrderResponse.statusReason = StringValue.of(reason)
        }
        messageWrapper.writeResponse(marketOrderResponse)
    }

    private fun writeErrorResponse(messageWrapper: MessageWrapper,
                                   order: MarketOrder,
                                   statusReason: String? = null) {
        writeResponse(messageWrapper, order, MessageStatusUtils.toMessageStatus(order.status), statusReason)
    }

    private fun sendErrorNotification(messageWrapper: MessageWrapper,
                                      order: MarketOrder,
                                      now: Date) {
        val marketOrderWithTrades = MarketOrderWithTrades(messageWrapper.messageId!!, order)
        val outgoingMessage = EventFactory.createExecutionEvent(messageSequenceNumberHolder.getNewValue(),
                messageWrapper.messageId!!,
                messageWrapper.id!!,
                now,
                MessageType.MARKET_ORDER,
                marketOrderWithTrades)
        messageSender.sendMessage(outgoingMessage)
    }

    fun parseMessage(messageWrapper: MessageWrapper) {
        val message = messageWrapper.parsedMessage!! as IncomingMessages.MarketOrder
        messageWrapper.messageId = if (message.hasMessageId()) message.messageId.value else message.uid
        messageWrapper.timestamp = message.timestamp.toDate().time
        messageWrapper.id = message.uid
        messageWrapper.processedMessage = ProcessedMessage(messageWrapper.type, messageWrapper.timestamp!!, messageWrapper.messageId!!)
    }

    override fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus) {
        messageWrapper.writeResponse(IncomingMessages.MarketOrderResponse.newBuilder()
                .setStatus(IncomingMessages.Status.forNumber(status.type)))
    }
}