package com.swisschain.matching.engine.utils

import com.google.protobuf.BoolValue
import com.google.protobuf.Int32Value
import com.google.protobuf.StringValue
import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.daos.FeeSizeType
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.MarketOrder
import com.swisschain.matching.engine.daos.context.SingleLimitOrderContext
import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.daos.fee.v2.NewLimitOrderFeeInstruction
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.order.OrderTimeInForce
import com.swisschain.matching.engine.daos.v2.FeeInstruction
import com.swisschain.matching.engine.daos.v2.LimitOrderFeeInstruction
import com.swisschain.matching.engine.grpc.TestMarketStreamObserver
import com.swisschain.matching.engine.grpc.TestMultiStreamObserver
import com.swisschain.matching.engine.grpc.TestStreamObserver
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderCancelOperationParsedData
import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderMassCancelOperationParsedData
import com.swisschain.matching.engine.incoming.parsers.impl.CashInOutContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.CashTransferContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.SingleLimitOrderContextParser
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MarketOrderMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.messages.MultiLimitOrderMessageWrapper
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.OrderCancelMode
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.services.validators.impl.OrderValidationResult
import java.math.BigDecimal
import java.util.Date
import java.util.UUID

class MessageBuilder(private var singleLimitOrderContextParser: SingleLimitOrderContextParser,
                     private val cashInOutContextParser: CashInOutContextParser,
                     private val cashTransferContextParser: CashTransferContextParser,
                     private val limitOrderCancelOperationContextParser: ContextParser<LimitOrderCancelOperationParsedData>,
                     private val limitOrderMassCancelOperationContextParser: ContextParser<LimitOrderMassCancelOperationParsedData>) {
companion object {
        fun buildLimitOrder(uid: String = UUID.randomUUID().toString(),
                            assetId: String = "EURUSD",
                            walletId: String = "Client1",
                            price: Double = 100.0,
                            registered: Date = Date(),
                            status: String = OrderStatus.InOrderBook.name,
                            volume: Double = 1000.0,
                            type: LimitOrderType = LimitOrderType.LIMIT,
                            lowerLimitPrice: Double? = null,
                            lowerPrice: Double? = null,
                            upperLimitPrice: Double? = null,
                            upperPrice: Double? = null,
                            reservedVolume: Double? = null,
                            fees: List<NewLimitOrderFeeInstruction> = listOf(),
                            previousExternalId: String? = null,
                            timeInForce: OrderTimeInForce? = null,
                            expiryTime: Date? = null): LimitOrder =
                LimitOrder(uid, uid, assetId, DEFAULT_BROKER, walletId, BigDecimal.valueOf(volume), BigDecimal.valueOf(price), status, registered, registered, registered, BigDecimal.valueOf(volume), null,
                        reservedVolume?.toBigDecimal(), fees,
                        type, lowerLimitPrice?.toBigDecimal(), lowerPrice?.toBigDecimal(),
                        upperLimitPrice?.toBigDecimal(), upperPrice?.toBigDecimal(),
                        previousExternalId,
                        timeInForce,
                        expiryTime,
                        null,
                        null)

        fun buildMarketOrderWrapper(order: MarketOrder): MarketOrderMessageWrapper {
            val id = UUID.randomUUID().toString()
            val builder = IncomingMessages.MarketOrder.newBuilder()
                    .setUid(id)
                    .setTimestamp(order.createdAt.createProtobufTimestampBuilder())
                    .setWalletId(order.walletId)
                    .setAssetPairId(order.assetPairId)
                    .setVolume(order.volume.toPlainString())
            order.fees?.forEach {
                builder.addFees(buildFee(it))
            }
            return MarketOrderMessageWrapper(MessageType.MARKET_ORDER.type, builder.build(), TestMarketStreamObserver(), false, id, id)
        }

        fun buildFee(fee: FeeInstruction): IncomingMessages.Fee {
            val builder = IncomingMessages.Fee.newBuilder().setType(fee.type.externalId)
            fee.size?.let {
                builder.size = StringValue.of(it.toPlainString())
            }
            fee.sourceWalletId?.let {
                builder.setSourceWalletId(StringValue.of(it))
            }
            fee.targetWalletId?.let {
                builder.setTargetWalletId(StringValue.of(it))
            }
            fee.sizeType?.let {
                builder.setSizeType(Int32Value.of(it.externalId))
            }
            if (fee is NewFeeInstruction) {
                builder.addAllAssetId(fee.assetIds)
            }
            return builder.build()
        }

        fun buildNewLimitOrderFee(fee: NewLimitOrderFeeInstruction): IncomingMessages.LimitOrderFee {
            val builder = IncomingMessages.LimitOrderFee.newBuilder().setType(fee.type.externalId)
            fee.size?.let {
                builder.takerSize = StringValue.of(it.toPlainString())
            }
            fee.sizeType?.let {
                builder.takerSizeType = Int32Value.of(it.externalId)
            }
            fee.makerSize?.let {
                builder.makerSize = StringValue.of(it.toPlainString())
            }
            fee.makerSizeType?.let {
                builder.makerSizeType = Int32Value.of(it.externalId)
            }
            fee.sourceWalletId?.let {
                builder.setSourceWalletId(StringValue.of(it))
            }
            fee.targetWalletId?.let {
                builder.setTargetWalletId(StringValue.of(it))
            }
            builder.addAllAssetId(fee.assetIds)
            return builder.build()
        }

        fun buildMarketOrder(rowKey: String = UUID.randomUUID().toString(),
                             assetId: String = "EURUSD",
                             walletId: String = "Client1",
                             registered: Date = Date(),
                             status: String = OrderStatus.InOrderBook.name,
                             straight: Boolean = true,
                             volume: Double = 1000.0,
                             reservedVolume: Double? = null,
                             fees: List<NewFeeInstruction> = listOf()): MarketOrder =
                MarketOrder(rowKey, rowKey, assetId, DEFAULT_BROKER, walletId,
                        BigDecimal.valueOf(volume), null, status, registered, registered, Date(),
                        null, straight,
                        reservedVolume?.toBigDecimal(),fees = fees)

        fun buildMultiLimitOrderWrapper(pair: String,
                                        walletId: String,
                                        orders: List<IncomingLimitOrder>,
                                        cancel: Boolean = true,
                                        cancelMode: OrderCancelMode? = null
        ): MultiLimitOrderMessageWrapper {
            val id = UUID.randomUUID().toString()
            return MultiLimitOrderMessageWrapper(MessageType.MULTI_LIMIT_ORDER.type, buildMultiLimitOrder(pair, walletId,
                    orders,
                    cancel,
                    cancelMode), TestMultiStreamObserver(), false, id, id)
        }

        private fun buildMultiLimitOrder(assetPairId: String,
                                         walletId: String,
                                         orders: List<IncomingLimitOrder>,
                                         cancel: Boolean,
                                         cancelMode: OrderCancelMode?): IncomingMessages.MultiLimitOrder {
            val multiOrderBuilder = IncomingMessages.MultiLimitOrder.newBuilder()
                    .setUid(UUID.randomUUID().toString())
                    .setTimestamp(Date().createProtobufTimestampBuilder())
                    .setWalletId(walletId)
                    .setAssetPairId(assetPairId)
                    .setCancelAllPreviousLimitOrders(BoolValue.of(cancel))
            cancelMode?.let { multiOrderBuilder.cancelMode = Int32Value.of(it.externalId) }
            orders.forEach { order ->
                val orderBuilder = IncomingMessages.MultiLimitOrder.Order.newBuilder()
                        .setVolume(order.volume.toString())
                order.price?.let { orderBuilder.price = StringValue.of(it.toString()) }
                order.feeInstructions.forEach { orderBuilder.addFees(buildNewLimitOrderFee(it)) }
                orderBuilder.uid = order.uid
                order.oldUid?.let { orderBuilder.oldUid = StringValue.of(order.oldUid) }
                order.timeInForce?.let { orderBuilder.timeInForce = Int32Value.of(it.externalId) }
                order.expiryTime?.let { orderBuilder.expiryTime = it.createProtobufTimestampBuilder().build() }
                order.type?.let { orderBuilder.type = Int32Value.of(it.externalId) }
                order.lowerLimitPrice?.let { orderBuilder.lowerLimitPrice = StringValue.of(it.toString()) }
                order.lowerPrice?.let { orderBuilder.lowerPrice = StringValue.of(it.toString()) }
                order.upperLimitPrice?.let { orderBuilder.upperLimitPrice = StringValue.of(it.toString()) }
                order.upperPrice?.let { orderBuilder.upperPrice = StringValue.of(it.toString()) }
                multiOrderBuilder.addOrders(orderBuilder.build())
            }
            return multiOrderBuilder.build()
        }

        fun buildMultiLimitOrderCancelWrapper(walletId: String, assetPairId: String, isBuy: Boolean): GenericMessageWrapper =
                GenericMessageWrapper(MessageType.LIMIT_ORDER_MASS_CANCEL.type, IncomingMessages.MultiLimitOrderCancel.newBuilder()
                .setUid(UUID.randomUUID().toString())
                .setTimestamp(Date().createProtobufTimestampBuilder())
                .setWalletId(walletId)
                .setAssetPairId(assetPairId)
                .setIsBuy(isBuy).build(), null, false)

        fun buildFeeInstructions(type: FeeType? = null,
                                 sizeType: FeeSizeType? = FeeSizeType.PERCENTAGE,
                                 size: Double? = null,
                                 sourceWalletId: String? = null,
                                 targetWalletId: String? = null,
                                 assetIds: List<String> = emptyList()): List<NewFeeInstruction> {
            return if (type == null) listOf()
            else return listOf(NewFeeInstruction(type, sizeType,
                    if (size != null) BigDecimal.valueOf(size) else null,
                    sourceWalletId, targetWalletId, assetIds))
        }

        fun buildFeeInstruction(type: FeeType? = null,
                                sizeType: FeeSizeType? = FeeSizeType.PERCENTAGE,
                                size: Double? = null,
                                sourceWalletId: String? = null,
                                targetWalletId: String? = null,
                                assetIds: List<String> = emptyList()): NewFeeInstruction? {
            return if (type == null) null
            else return NewFeeInstruction(type, sizeType,
                    if (size != null) BigDecimal.valueOf(size) else null,
                    sourceWalletId, targetWalletId, assetIds)
        }

        fun buildLimitOrderFeeInstruction(type: FeeType? = null,
                                          takerSizeType: FeeSizeType? = FeeSizeType.PERCENTAGE,
                                          takerSize: Double? = null,
                                          makerSizeType: FeeSizeType? = FeeSizeType.PERCENTAGE,
                                          makerSize: Double? = null,
                                          sourceWalletId: String? = null,
                                          targetWalletId: String? = null): LimitOrderFeeInstruction? {
            return if (type == null) null
            else return LimitOrderFeeInstruction(type, takerSizeType,
                    if (takerSize != null) BigDecimal.valueOf(takerSize) else null,
                    makerSizeType,
                    if (makerSize != null) BigDecimal.valueOf(makerSize) else null,
                    sourceWalletId,
                    targetWalletId)
        }

        fun buildLimitOrderFeeInstructions(type: FeeType? = null,
                                           takerSizeType: FeeSizeType? = FeeSizeType.PERCENTAGE,
                                           takerSize: Double? = null,
                                           makerSizeType: FeeSizeType? = FeeSizeType.PERCENTAGE,
                                           makerSize: Double? = null,
                                           sourceWalletId: String? = null,
                                           targetWalletId: String? = null,
                                           assetIds: List<String> = emptyList(),
                                           makerFeeModificator: Double? = null): List<NewLimitOrderFeeInstruction> {
            return if (type == null) listOf()
            else return listOf(NewLimitOrderFeeInstruction(type, takerSizeType,
                    if (takerSize != null) BigDecimal.valueOf(takerSize) else null,
                    makerSizeType,
                    if (makerSize != null) BigDecimal.valueOf(makerSize) else null,
                    sourceWalletId, targetWalletId, assetIds,
                    if (makerFeeModificator != null) BigDecimal.valueOf(makerFeeModificator) else null))
        }
    }

    fun buildTransferWrapper(fromWalletId: String,
                             toWalletId: String,
                             assetId: String,
                             amount: Double,
                             overdraftLimit: Double,
                             businessId: String = UUID.randomUUID().toString()
    ): MessageWrapper {
        return cashTransferContextParser.parse(GenericMessageWrapper(MessageType.CASH_TRANSFER_OPERATION.type, IncomingMessages.CashTransferOperation.newBuilder()
                .setId(businessId)
                .setFromWalletId(fromWalletId)
                .setToWalletId(toWalletId)
                .setAssetId(assetId)
                .setVolume(amount.toString())
                .setOverdraftLimit(StringValue.of(overdraftLimit.toString()))
                .setTimestamp(Date().createProtobufTimestampBuilder()).build(), null, false)).messageWrapper
    }

    fun buildCashInOutWrapper(walletId: String, assetId: String, amount: Double, businessId: String = UUID.randomUUID().toString(),
                              fees: List<NewFeeInstruction> = listOf()): MessageWrapper {
        val builder = IncomingMessages.CashInOutOperation.newBuilder()
                .setId(businessId)
                .setWalletId(walletId)
                .setAssetId(assetId)
                .setVolume(amount.toString())
                .setTimestamp(Date().createProtobufTimestampBuilder())
        fees.forEach {
            builder.addFees(buildFee(it))
        }

        return cashInOutContextParser.parse(GenericMessageWrapper(MessageType.CASH_IN_OUT_OPERATION.type, builder.build(), null, false)).messageWrapper
    }

    fun buildLimitOrderCancelWrapper(uid: String) = buildLimitOrderCancelWrapper(listOf(uid))

    fun buildLimitOrderCancelWrapper(uids: List<String>): MessageWrapper {
        val parsedData = limitOrderCancelOperationContextParser.parse(GenericMessageWrapper(MessageType.LIMIT_ORDER_CANCEL.type, IncomingMessages.LimitOrderCancel.newBuilder()
                .setUid(UUID.randomUUID().toString()).addAllLimitOrderId(uids).build(), null, false))
        return parsedData.messageWrapper
    }

    fun buildLimitOrderMassCancelWrapper(walletId: String? = null,
                                         assetPairId: String? = null,
                                         isBuy: Boolean? = null): MessageWrapper {
        val builder = IncomingMessages.LimitOrderMassCancel.newBuilder()
                .setUid(UUID.randomUUID().toString())
        walletId?.let {
            builder.setWalletId(StringValue.of(it))
        }
        assetPairId?.let {
            builder.setAssetPairId(StringValue.of(it))
        }
        isBuy?.let {
            builder.setIsBuy(BoolValue.of(it))
        }

        val messageWrapper = GenericMessageWrapper(MessageType.LIMIT_ORDER_MASS_CANCEL.type, builder.build(), null, false)
        return limitOrderMassCancelOperationContextParser.parse(messageWrapper).messageWrapper
    }

    fun buildLimitOrderWrapper(order: LimitOrder,
                               cancel: Boolean = false): GenericMessageWrapper {
        val builder = IncomingMessages.LimitOrder.newBuilder()
                .setUid(order.externalId)
                .setTimestamp(order.createdAt.createProtobufTimestampBuilder())
                .setWalletId(order.walletId)
                .setAssetPairId(order.assetPairId)
                .setVolume(order.volume.toPlainString())
                .setCancelAllPreviousLimitOrders(BoolValue.of(cancel))
                .setType(IncomingMessages.LimitOrder.LimitOrderType.forNumber(order.type!!.externalId))
        if (order.type == LimitOrderType.LIMIT) {
            builder.price = StringValue.of(order.price.toPlainString())
        }
        order.fees?.forEach {
            builder.addFees(buildNewLimitOrderFee(it as NewLimitOrderFeeInstruction))
        }
        order.lowerLimitPrice?.let { builder.setLowerLimitPrice(StringValue.of(it.toPlainString())) }
        order.lowerPrice?.let { builder.setLowerPrice(StringValue.of(it.toPlainString())) }
        order.upperLimitPrice?.let { builder.setUpperLimitPrice(StringValue.of(it.toPlainString())) }
        order.upperPrice?.let { builder.setUpperPrice(StringValue.of(it.toPlainString())) }
        order.expiryTime?.let { builder.setExpiryTime(it.createProtobufTimestampBuilder()) }
        order.timeInForce?.let { builder.setTimeInForce(IncomingMessages.LimitOrder.OrderTimeInForce.forNumber(it.externalId)) }
        val messageWrapper = singleLimitOrderContextParser
                .parse(GenericMessageWrapper(MessageType.LIMIT_ORDER.type, builder.build(), TestStreamObserver(), false))
                .messageWrapper as GenericMessageWrapper

        val singleLimitContext = messageWrapper.context as SingleLimitOrderContext
        singleLimitContext.validationResult = OrderValidationResult(true)

        return messageWrapper
    }
}