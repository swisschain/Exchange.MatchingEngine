package com.swisschain.matching.engine.services

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.MultiLimitOrder
import com.swisschain.matching.engine.daos.fee.v2.NewLimitOrderFeeInstruction
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.order.OrderTimeInForce
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.ExecutionDataApplyService
import com.swisschain.matching.engine.order.OrderCancelMode
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.order.process.GenericLimitOrdersProcessor
import com.swisschain.matching.engine.order.process.PreviousLimitOrdersProcessor
import com.swisschain.matching.engine.order.process.StopOrderBookProcessor
import com.swisschain.matching.engine.order.transaction.ExecutionContextFactory
import com.swisschain.matching.engine.outgoing.messages.v2.toDate
import com.swisschain.matching.engine.services.utils.MultiOrderFilter
import com.swisschain.matching.engine.utils.order.MessageStatusUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.Date

@Service
class MultiLimitOrderService(private val executionContextFactory: ExecutionContextFactory,
                             private val genericLimitOrdersProcessor: GenericLimitOrdersProcessor,
                             private val stopOrderBookProcessor: StopOrderBookProcessor,
                             private val executionDataApplyService: ExecutionDataApplyService,
                             private val previousLimitOrdersProcessor: PreviousLimitOrdersProcessor,
                             private val assetsHolder: AssetsHolder,
                             private val assetsPairsHolder: AssetsPairsHolder,
                             private val balancesHolder: BalancesHolder,
                             private val applicationSettingsHolder: ApplicationSettingsHolder,
                             private val messageProcessingStatusHolder: MessageProcessingStatusHolder,
                             private val uuidHolder: UUIDHolder) : AbstractService {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MultiLimitOrderService::class.java.name)
    }

    override fun processMessage(messageWrapper: MessageWrapper) {
        val message = messageWrapper.parsedMessage!! as IncomingMessages.MultiLimitOrder
        messageWrapper.messageId = if (message.hasMessageId()) message.messageId.value else message.uid
        messageWrapper.id = message.uid

        val assetPair = assetsPairsHolder.getAssetPairAllowNulls(message.brokerId, message.assetPairId)
        if (assetPair == null) {
            LOGGER.info("Unable to process message (${messageWrapper.messageId}): unknown asset pair ${message.assetPairId}")
            writeResponse(messageWrapper, MessageStatus.UNKNOWN_ASSET)
            return
        }

        if (messageProcessingStatusHolder.isTradeDisabled(assetPair)) {
            writeResponse(messageWrapper, MessageStatus.MESSAGE_PROCESSING_DISABLED)
            return
        }

        val isTrustedClient = applicationSettingsHolder.isTrustedClient(message.walletId)

        val multiLimitOrder = readMultiLimitOrder(messageWrapper.messageId!!, message, isTrustedClient, assetPair)
        val now = Date()

        val executionContext = executionContextFactory.create(messageWrapper.messageId!!,
                messageWrapper.id!!,
                MessageType.MULTI_LIMIT_ORDER,
                messageWrapper.processedMessage,
                mapOf(Pair(assetPair.assetPairId, assetPair)),
                now,
                LOGGER)

        previousLimitOrdersProcessor.cancelAndReplaceOrders(multiLimitOrder.brokerId,
                multiLimitOrder.walletId,
                multiLimitOrder.assetPairId,
                multiLimitOrder.cancelAllPreviousLimitOrders,
                multiLimitOrder.cancelBuySide,
                multiLimitOrder.cancelSellSide,
                multiLimitOrder.buyReplacements,
                multiLimitOrder.sellReplacements,
                executionContext)

        val processedOrders = genericLimitOrdersProcessor.processOrders(multiLimitOrder.orders, executionContext)
        stopOrderBookProcessor.checkAndExecuteStopLimitOrders(executionContext)
        val persisted = executionDataApplyService.persistAndSendEvents(messageWrapper, executionContext)

        val responseBuilder = IncomingMessages.MultiLimitOrderResponse.newBuilder()
        if (!persisted) {
            val errorMessage = "Unable to save result data"
            LOGGER.error("$errorMessage (multi limit order id ${multiLimitOrder.messageUid})")

            messageWrapper.writeResponse(responseBuilder
                    .setStatus(IncomingMessages.Status.RUNTIME)
                    .setAssetPairId(StringValue.of(multiLimitOrder.assetPairId))
                    .setStatusReason(StringValue.of(errorMessage)))

            return
        }

        responseBuilder.setId(StringValue.of(multiLimitOrder.messageUid))
                .setStatus(IncomingMessages.Status.OK).assetPairId = StringValue.of(multiLimitOrder.assetPairId)

        processedOrders.forEach { processedOrder ->
            val order = processedOrder.order
            val statusBuilder = IncomingMessages.MultiLimitOrderResponse.OrderStatus.newBuilder()
                    .setId(order.externalId)
                    .setMatchingEngineId(StringValue.of(order.id))
                    .setStatus(IncomingMessages.Status.forNumber(MessageStatusUtils.toMessageStatus(order.status).type))
                    .setVolume(order.volume.toPlainString())
                    .setPrice(order.price.toPlainString())
            processedOrder.reason?.let { statusBuilder.statusReason = StringValue.of(processedOrder.reason) }
            responseBuilder.addStatuses(statusBuilder)
        }
        writeResponse(messageWrapper, responseBuilder)

    }

    private fun readMultiLimitOrder(messageId: String,
                                    message: IncomingMessages.MultiLimitOrder,
                                    isTrustedClient: Boolean,
                                    assetPair: AssetPair): MultiLimitOrder {
        LOGGER.debug("Got ${if (!isTrustedClient) "client " else ""}multi limit order id: ${message.uid}, " +
                (if (messageId != message.uid) "messageId: $messageId, " else "") +
                "client ${message.walletId}, " +
                "assetPair: ${message.assetPairId}, " +
                "ordersCount: ${message.ordersCount}, " +
                (if (message.hasCancelAllPreviousLimitOrders()) "cancelPrevious: ${message.cancelAllPreviousLimitOrders}, " else "") +
                (if (message.hasCancelMode()) "cancelMode: ${message.cancelMode}" else ""))

        val walletId = message.walletId
        val messageUid = message.uid
        val assetPairId = message.assetPairId
        val cancelAllPreviousLimitOrders = message.cancelAllPreviousLimitOrders
        val cancelMode = if (message.hasCancelMode()) OrderCancelMode.getByExternalId(message.cancelMode.value) else OrderCancelMode.NOT_EMPTY_SIDE
        val now = Date()
        var cancelBuySide = cancelMode == OrderCancelMode.BUY_SIDE || cancelMode == OrderCancelMode.BOTH_SIDES
        var cancelSellSide = cancelMode == OrderCancelMode.SELL_SIDE || cancelMode == OrderCancelMode.BOTH_SIDES

        val buyReplacements = mutableMapOf<String, LimitOrder>()
        val sellReplacements = mutableMapOf<String, LimitOrder>()

        val baseAssetAvailableBalance = balancesHolder.getAvailableBalance(message.brokerId, walletId, assetPair.baseAssetId)
        val quotingAssetAvailableBalance = balancesHolder.getAvailableBalance(message.brokerId, walletId, assetPair.quotingAssetId)

        val filter = MultiOrderFilter(isTrustedClient,
                baseAssetAvailableBalance,
                quotingAssetAvailableBalance,
                assetsHolder.getAsset(message.brokerId, assetPair.quotingAssetId).accuracy,
                now,
                message.ordersList.size,
                LOGGER)

        message.ordersList.forEach { currentOrder ->
            if (!isTrustedClient) {
                LOGGER.debug("Incoming limit order (message id: $messageId): ${getIncomingOrderInfo(currentOrder)}")
            }
            val type = if (currentOrder.hasType()) LimitOrderType.getByExternalId(currentOrder.type.value) else LimitOrderType.LIMIT
            val status = when(type) {
                LimitOrderType.LIMIT -> OrderStatus.InOrderBook
                LimitOrderType.STOP_LIMIT -> OrderStatus.Pending
            }
            val price = if (currentOrder.hasPrice()) BigDecimal(currentOrder.price.value) else BigDecimal.ZERO
            val lowerLimitPrice = if (currentOrder.hasLowerLimitPrice()) BigDecimal(currentOrder.lowerLimitPrice.value) else null
            val lowerPrice = if (currentOrder.hasLowerPrice()) BigDecimal(currentOrder.lowerPrice.value) else null
            val upperLimitPrice = if (currentOrder.hasUpperLimitPrice()) BigDecimal(currentOrder.upperLimitPrice.value) else null
            val upperPrice = if (currentOrder.hasUpperPrice()) BigDecimal(currentOrder.upperPrice.value) else null
            val feeInstructions = NewLimitOrderFeeInstruction.create(currentOrder.feesList)
            val previousExternalId = if (currentOrder.hasOldUid()) currentOrder.oldUid.value else null

            val order = LimitOrder(uuidHolder.getNextValue(),
                    currentOrder.uid,
                    message.assetPairId,
                    message.brokerId,
                    message.walletId,
                    BigDecimal(currentOrder.volume),
                    price,
                    status.name,
                    now,
                    if (message.hasTimestamp()) message.timestamp.toDate() else now,
                    now,
                    BigDecimal(currentOrder.volume),
                    null,
                    fees = feeInstructions,
                    type = type,
                    lowerLimitPrice = lowerLimitPrice,
                    lowerPrice = lowerPrice,
                    upperLimitPrice = upperLimitPrice,
                    upperPrice = upperPrice,
                    previousExternalId = previousExternalId,
                    timeInForce = if (currentOrder.hasTimeInForce()) OrderTimeInForce.getByExternalId(currentOrder.timeInForce.value) else null,
                    expiryTime = if (currentOrder.hasExpiryTime()) currentOrder.expiryTime.toDate() else null,
                    parentOrderExternalId = null,
                    childOrderExternalId = null)

            filter.checkAndAdd(order)
            previousExternalId?.let {
                (if (order.isBuySide()) buyReplacements else sellReplacements)[it] = order
            }

            if (cancelAllPreviousLimitOrders.value && cancelMode == OrderCancelMode.NOT_EMPTY_SIDE) {
                if (BigDecimal(currentOrder.volume) > BigDecimal.ZERO) {
                    cancelBuySide = true
                } else {
                    cancelSellSide = true
                }
            }
        }

        return MultiLimitOrder(messageUid,
                message.brokerId,
                walletId,
                assetPairId,
                filter.getResult(),
                cancelAllPreviousLimitOrders.value,
                cancelBuySide,
                cancelSellSide,
                cancelMode,
                buyReplacements,
                sellReplacements)
    }

    private fun getIncomingOrderInfo(incomingOrder: IncomingMessages.MultiLimitOrder.Order): String {
        return "id: ${incomingOrder.uid}" +
                (if (incomingOrder.hasType()) ", type: ${incomingOrder.type}" else "") +
                ", volume: ${incomingOrder.volume}" +
                (if (incomingOrder.hasPrice()) ", price: ${incomingOrder.price.value}" else "") +
                (if (incomingOrder.hasLowerLimitPrice()) ", lowerLimitPrice: ${incomingOrder.lowerLimitPrice.value}" else "") +
                (if (incomingOrder.hasLowerPrice()) ", lowerPrice: ${incomingOrder.lowerPrice.value}" else "") +
                (if (incomingOrder.hasUpperLimitPrice()) ", upperLimitPrice: ${incomingOrder.upperLimitPrice.value}" else "") +
                (if (incomingOrder.hasUpperPrice()) ", upperPrice: ${incomingOrder.upperPrice.value}" else "") +
                (if (incomingOrder.hasOldUid()) ", oldUid: ${incomingOrder.oldUid}" else "") +
                (if (incomingOrder.hasTimeInForce()) ", timeInForce: ${incomingOrder.timeInForce}" else "") +
                (if (incomingOrder.hasExpiryTime()) ", expiryTime: ${incomingOrder.expiryTime}" else "") +
                (if (incomingOrder.feesCount > 0) ", fees: ${incomingOrder.feesList.asSequence().joinToString(", ")}" else "")
    }

    fun writeResponse(messageWrapper: MessageWrapper, responseBuilder: IncomingMessages.MultiLimitOrderResponse.Builder) {
        messageWrapper.writeResponse(responseBuilder)
    }

    override fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus) {
        val assetPairId = (messageWrapper.parsedMessage as IncomingMessages.MultiLimitOrder).assetPairId
        messageWrapper.writeResponse(IncomingMessages.MultiLimitOrderResponse.newBuilder()
                .setStatus(IncomingMessages.Status.forNumber(status.type)).setAssetPairId(StringValue.of(assetPairId)))
    }
}