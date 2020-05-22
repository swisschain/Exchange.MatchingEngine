package com.swisschain.matching.engine.incoming.parsers.impl

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.context.SingleLimitOrderContext
import com.swisschain.matching.engine.daos.fee.v2.NewLimitOrderFeeInstruction
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.order.OrderTimeInForce
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.SingleLimitOrderParsedData
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.outgoing.messages.v2.toDate
import com.swisschain.utils.logging.ThrottlingLogger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Date

@Component
class SingleLimitOrderContextParser(private val assetsPairsHolder: AssetsPairsHolder,
                                    private val assetsHolder: AssetsHolder,
                                    private val applicationSettingsHolder: ApplicationSettingsHolder,
                                    private val uuidHolder: UUIDHolder,
                                    @Qualifier("singleLimitOrderPreProcessingLogger")
                                    private val logger: ThrottlingLogger) : ContextParser<SingleLimitOrderParsedData> {

    override fun parse(messageWrapper: MessageWrapper): SingleLimitOrderParsedData {

        val context = parseMessage(messageWrapper)

        messageWrapper.context = context
        messageWrapper.id = context.limitOrder.externalId
        messageWrapper.messageId = context.messageId
        messageWrapper.timestamp = context.processedMessage?.timestamp
        messageWrapper.processedMessage = context.processedMessage

        return SingleLimitOrderParsedData(messageWrapper, context.limitOrder.assetPairId)
    }

    private fun getContext(messageId: String,
                           order: LimitOrder, cancelOrders: Boolean,
                           processedMessage: ProcessedMessage?): SingleLimitOrderContext {
        val builder = SingleLimitOrderContext.Builder()
        val assetPair = getAssetPair(order.brokerId, order.assetPairId)

        builder.messageId(messageId)
                .limitOrder(order)
                .assetPair(assetPair)
                .baseAsset(assetPair?.let { getBaseAsset(it) })
                .quotingAsset(assetPair?.let { getQuotingAsset(it) })
                .trustedClient(getTrustedClient(builder.limitOrder.walletId))
                .limitAsset(assetPair?.let { getLimitAsset(order, assetPair) })
                .cancelOrders(cancelOrders)
                .processedMessage(processedMessage)

        return builder.build()
    }

    private fun getLimitAsset(order: LimitOrder, assetPair: AssetPair): Asset? {
        return assetsHolder.getAssetAllowNulls(order.brokerId, if (order.isBuySide()) assetPair.quotingAssetId else assetPair.baseAssetId)
    }

    private fun getTrustedClient(walletId: Long): Boolean {
        return applicationSettingsHolder.isTrustedClient(walletId)
    }

    fun getAssetPair(brokerId: String, assetPairId: String): AssetPair? {
        return assetsPairsHolder.getAssetPairAllowNulls(brokerId, assetPairId)
    }

    private fun getBaseAsset(assetPair: AssetPair): Asset? {
        return assetsHolder.getAssetAllowNulls(assetPair.brokerId, assetPair.baseAssetId)
    }

    private fun getQuotingAsset(assetPair: AssetPair): Asset? {
        return assetsHolder.getAssetAllowNulls(assetPair.brokerId, assetPair.quotingAssetId)
    }

    private fun parseMessage(messageWrapper: MessageWrapper): SingleLimitOrderContext {
        val message = messageWrapper.parsedMessage as IncomingMessages.LimitOrder
        val messageId = if (message.hasMessageId()) message.messageId.value else message.id

        val limitOrder = createOrder(message)

        val singleLimitOrderContext = getContext(messageId, limitOrder, message.cancelAllPreviousLimitOrders.value,
                ProcessedMessage(messageWrapper.type, message.timestamp.seconds, messageId))

        logger.info("Got limit order  messageId: $messageId, id: ${message.id}, client ${message.walletId}")

        return singleLimitOrderContext
    }
    private fun createOrder(message: IncomingMessages.LimitOrder): LimitOrder {
        val type = LimitOrderType.getByExternalId(message.typeValue)
        val status = when (type) {
            LimitOrderType.LIMIT -> OrderStatus.InOrderBook
            LimitOrderType.STOP_LIMIT -> OrderStatus.Pending
        }
        val feeInstructions = NewLimitOrderFeeInstruction.create(message.feesList)
        return LimitOrder(
                uuidHolder.getNextValue(),
                message.id,
                message.assetPairId,
                message.brokerId,
                message.accountId,
                message.walletId,
                BigDecimal(message.volume),
                if (message.hasPrice()) BigDecimal(message.price.value) else BigDecimal.ZERO,
                status.name,
                null,
                if (message.hasTimestamp()) message.timestamp.toDate() else Date(),
                null,
                BigDecimal(message.volume),
                null,
                fees = feeInstructions,
                type = type,
                lowerLimitPrice = if (message.hasLowerLimitPrice()) BigDecimal(message.lowerLimitPrice.value) else null,
                lowerPrice = if (message.hasLowerPrice()) BigDecimal(message.lowerPrice.value) else null,
                upperLimitPrice = if (message.hasUpperLimitPrice()) BigDecimal(message.upperLimitPrice.value) else null,
                upperPrice = if (message.hasUpperPrice()) BigDecimal(message.upperPrice.value) else null,
                previousExternalId = null,
                timeInForce = OrderTimeInForce.getByExternalId(message.timeInForceValue),
                expiryTime = if (message.hasExpiryTime()) message.expiryTime.toDate() else null,
                parentOrderExternalId = null,
                childOrderExternalId = null
        )
    }
}