package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.outgoing.messages.ClientBalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.LimitOrderWithTrades
import com.swisschain.matching.engine.outgoing.messages.LimitTradeInfo
import com.swisschain.matching.engine.outgoing.messages.MarketOrderWithTrades
import com.swisschain.matching.engine.outgoing.messages.TradeInfo
import com.swisschain.matching.engine.outgoing.messages.v2.enums.FeeSizeType
import com.swisschain.matching.engine.outgoing.messages.v2.enums.FeeType
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderSide
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderTimeInForce
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderType
import com.swisschain.matching.engine.outgoing.messages.v2.enums.TradeRole
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Fee
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.FeeInstruction
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.FeeTransfer
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Order
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Trade
import java.math.BigDecimal
import java.util.Date


fun convertFees(fees: List<com.swisschain.matching.engine.daos.fee.v2.Fee>): List<Fee> {
    return fees.mapIndexed { index, fee ->
        Fee(convertFeeInstruction(index, fee.instruction), convertFeeTransfer(index, fee.transfer))
    }
}

fun convertBalanceUpdates(clientBalanceUpdates: List<ClientBalanceUpdate>): List<BalanceUpdate> {
    return clientBalanceUpdates.filter{
        it.newBalance.compareTo(it.oldBalance) != 0 || it.newReserved.compareTo(it.oldReserved) != 0
    }.map { balanceUpdate ->
        BalanceUpdate(balanceUpdate.brokerId,
                balanceUpdate.walletId,
                balanceUpdate.asset,
                bigDecimalToString(balanceUpdate.oldBalance)!!,
                bigDecimalToString(balanceUpdate.newBalance)!!,
                bigDecimalToString(balanceUpdate.oldReserved)!!,
                bigDecimalToString(balanceUpdate.newReserved)!!)
    }
}

fun convertOrders(limitOrdersWithTrades: List<LimitOrderWithTrades>, marketOrderWithTrades: MarketOrderWithTrades?): List<Order> {
    val result = mutableListOf<Order>()
    limitOrdersWithTrades.map { limitOrderWithTrades ->
        result.add(convertLimitOrder(limitOrderWithTrades))
    }
    marketOrderWithTrades?.let {
        result.add(convertMarketOrder(it))
    }
    return result.toList()
}

private fun convertLimitOrder(limitOrderWithTrades: LimitOrderWithTrades): Order {
    val limitOrder = limitOrderWithTrades.order
    val statusInfo = orderStatusInfo(limitOrder)
    val feeInstructions = if (limitOrderWithTrades.trades.isEmpty()) limitOrder.fees else limitOrderWithTrades.trades.first().fees.map { it.instruction }
    return Order(orderType(limitOrder),
            limitOrder.id,
            limitOrder.externalId,
            limitOrder.assetPairId,
            limitOrder.brokerId,
            limitOrder.accountId,
            limitOrder.walletId,
            orderSide(limitOrder),
            bigDecimalToString(limitOrder.volume)!!,
            bigDecimalToString(limitOrder.remainingVolume),
            bigDecimalToString(limitOrder.price),
            statusInfo.status,
            statusInfo.rejectReason,
            limitOrder.statusDate ?: Date(),
            limitOrder.createdAt,
            limitOrder.registered!!,
            limitOrder.lastMatchTime,
            bigDecimalToString(limitOrder.lowerLimitPrice),
            bigDecimalToString(limitOrder.lowerPrice),
            bigDecimalToString(limitOrder.upperLimitPrice),
            bigDecimalToString(limitOrder.upperPrice),
            null,
            feeInstructions?.mapIndexed { index, feeInstruction -> convertFeeInstruction(index, feeInstruction) },
            limitOrderWithTrades.trades.map { convertTrade(it) },
            orderTimeInForce(limitOrder.timeInForce),
            limitOrder.expiryTime,
            limitOrder.parentOrderExternalId,
            limitOrder.childOrderExternalId)
}

private fun convertMarketOrder(marketOrderWithTrades: MarketOrderWithTrades): Order {
    val marketOrder = marketOrderWithTrades.order
    val statusInfo = orderStatusInfo(marketOrder)
    val feeInstructions = if (marketOrderWithTrades.trades.isEmpty()) marketOrder.fees else marketOrderWithTrades.trades.first().fees.map { it.instruction }
    return Order(OrderType.MARKET,
            marketOrder.id,
            marketOrder.externalId,
            marketOrder.assetPairId,
            marketOrder.brokerId,
            marketOrder.accountId,
            marketOrder.walletId,
            orderSide(marketOrder),
            bigDecimalToString(marketOrder.volume)!!,
            null,
            bigDecimalToString(marketOrder.price),
            statusInfo.status,
            statusInfo.rejectReason,
            marketOrder.statusDate!!,
            marketOrder.createdAt,
            marketOrder.registered!!,
            marketOrder.matchedAt,
            null,
            null,
            null,
            null,
            marketOrder.isStraight(),
            feeInstructions?.mapIndexed { index, feeInstruction -> convertFeeInstruction(index, feeInstruction) },
            marketOrderWithTrades.trades.map { convertTrade(it) },
            null,
            null,
            null,
            null)
}

private fun orderType(order: LimitOrder): OrderType {
    return when (order.type) {
        LimitOrderType.LIMIT -> OrderType.LIMIT
        LimitOrderType.STOP_LIMIT -> OrderType.STOP_LIMIT
        null -> OrderType.LIMIT
    }
}

private fun orderSide(order: com.swisschain.matching.engine.daos.Order): OrderSide {
    return if (order.isOrigBuySide()) OrderSide.BUY else OrderSide.SELL
}

private class OrderStatusInfo(val status: OrderStatus,
                              val rejectReason: OrderRejectReason? = null)

private fun orderStatusInfo(order: com.swisschain.matching.engine.daos.Order): OrderStatusInfo {
    return when (order.status) {
        com.swisschain.matching.engine.order.OrderStatus.InOrderBook.name -> OrderStatusInfo(OrderStatus.PLACED)
        com.swisschain.matching.engine.order.OrderStatus.Processing.name -> OrderStatusInfo(OrderStatus.PARTIALLY_MATCHED)
        com.swisschain.matching.engine.order.OrderStatus.Pending.name -> OrderStatusInfo(OrderStatus.PENDING)
        com.swisschain.matching.engine.order.OrderStatus.Executed.name -> OrderStatusInfo(OrderStatus.EXECUTED)
        com.swisschain.matching.engine.order.OrderStatus.Cancelled.name -> OrderStatusInfo(OrderStatus.CANCELLED)
        com.swisschain.matching.engine.order.OrderStatus.Replaced.name -> OrderStatusInfo(OrderStatus.REPLACED)
        com.swisschain.matching.engine.order.OrderStatus.Matched.name -> OrderStatusInfo(OrderStatus.MATCHED)
        com.swisschain.matching.engine.order.OrderStatus.NotEnoughFunds.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.NOT_ENOUGH_FUNDS)
        com.swisschain.matching.engine.order.OrderStatus.ReservedVolumeGreaterThanBalance.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.RESERVED_VOLUME_GREATER_THAN_BALANCE)
        com.swisschain.matching.engine.order.OrderStatus.NoLiquidity.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.NO_LIQUIDITY)
        com.swisschain.matching.engine.order.OrderStatus.UnknownAsset.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.UNKNOWN_ASSET)
        com.swisschain.matching.engine.order.OrderStatus.DisabledAsset.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.DISABLED_ASSET)
        com.swisschain.matching.engine.order.OrderStatus.LeadToNegativeSpread.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.LEAD_TO_NEGATIVE_SPREAD)
        com.swisschain.matching.engine.order.OrderStatus.InvalidFee.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.INVALID_FEE)
        com.swisschain.matching.engine.order.OrderStatus.TooSmallVolume.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.TOO_SMALL_VOLUME)
        com.swisschain.matching.engine.order.OrderStatus.InvalidPrice.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.INVALID_PRICE)
        com.swisschain.matching.engine.order.OrderStatus.NotFoundPrevious.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.NOT_FOUND_PREVIOUS)
        com.swisschain.matching.engine.order.OrderStatus.InvalidPriceAccuracy.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.INVALID_PRICE_ACCURACY)
        com.swisschain.matching.engine.order.OrderStatus.InvalidVolumeAccuracy.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.INVALID_VOLUME_ACCURACY)
        com.swisschain.matching.engine.order.OrderStatus.InvalidVolume.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.INVALID_VOLUME)
        com.swisschain.matching.engine.order.OrderStatus.InvalidValue.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.INVALID_VALUE)
        com.swisschain.matching.engine.order.OrderStatus.TooHighPriceDeviation.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.TOO_HIGH_PRICE_DEVIATION)
        com.swisschain.matching.engine.order.OrderStatus.OrderBookMaxSizeReached.name -> OrderStatusInfo(OrderStatus.REJECTED, OrderRejectReason.ORDER_BOOK_MAX_SIZE_REACHED)
        else -> OrderStatusInfo(OrderStatus.UNKNOWN_STATUS)
    }
}

private fun convertTrade(tradeInfo: LimitTradeInfo): Trade {
    return Trade(tradeInfo.tradeId,
            tradeInfo.baseAssetId,
            tradeInfo.baseVolume,
            bigDecimalToString(tradeInfo.price)!!,
            tradeInfo.timestamp,
            tradeInfo.oppositeOrderId,
            tradeInfo.oppositeOrderExternalId,
            tradeInfo.oppositeWalletId,
            tradeInfo.quotingAssetId,
            tradeInfo.quotingVolume,
            tradeInfo.index.toInt(),
            bigDecimalToString(tradeInfo.absoluteSpread),
            bigDecimalToString(tradeInfo.relativeSpread),
            tradeInfo.role,
            tradeInfo.fees.mapIndexed { index, fee -> convertFeeTransfer(index, fee.transfer) }.filterNotNull()
    )
}

private fun convertTrade(tradeInfo: TradeInfo): Trade {
    return Trade(tradeInfo.tradeId,
            tradeInfo.baseAssetId,
            tradeInfo.baseVolume,
            bigDecimalToString(tradeInfo.price)!!,
            tradeInfo.timestamp,
            tradeInfo.limitOrderId,
            tradeInfo.limitOrderExternalId,
            tradeInfo.limitWalletId,
            tradeInfo.quotingAssetId,
            tradeInfo.quotingVolume,
            tradeInfo.index.toInt(),
            bigDecimalToString(tradeInfo.absoluteSpread),
            bigDecimalToString(tradeInfo.relativeSpread),
            TradeRole.TAKER,
            tradeInfo.fees.mapIndexed { index, fee -> convertFeeTransfer(index, fee.transfer) }.filterNotNull())
}

fun bigDecimalToString(value: BigDecimal?): String? = value?.stripTrailingZeros()?.toPlainString()

private fun convertFeeInstruction(index: Int, internalFeeInstruction: com.swisschain.matching.engine.daos.v2.FeeInstruction): FeeInstruction {
    val assetIds = (internalFeeInstruction as? NewFeeInstruction)?.assetIds
    return FeeInstruction(convertFeeType(internalFeeInstruction.type),
            bigDecimalToString(internalFeeInstruction.size),
            convertFeeSizeType(internalFeeInstruction.sizeType),
            null,
            null,
            internalFeeInstruction.sourceWalletId,
            internalFeeInstruction.targetWalletId,
            assetIds,
            null,
            index)
}

private fun convertFeeType(internalFeeType: com.swisschain.matching.engine.daos.FeeType): FeeType {
    return when (internalFeeType) {
        com.swisschain.matching.engine.daos.FeeType.CLIENT_FEE -> FeeType.WALLET_FEE
        com.swisschain.matching.engine.daos.FeeType.EXTERNAL_FEE -> FeeType.EXTERNAL_FEE
        com.swisschain.matching.engine.daos.FeeType.NO_FEE -> FeeType.NO_FEE
    }
}

private fun convertFeeSizeType(internalFeeSizeType: com.swisschain.matching.engine.daos.FeeSizeType?): FeeSizeType? {
    return when (internalFeeSizeType) {
        com.swisschain.matching.engine.daos.FeeSizeType.ABSOLUTE -> FeeSizeType.ABSOLUTE
        com.swisschain.matching.engine.daos.FeeSizeType.PERCENTAGE -> FeeSizeType.PERCENTAGE
        null -> null
    }
}

private fun convertFeeTransfer(index: Int, internalFeeTransfer: com.swisschain.matching.engine.daos.FeeTransfer?): FeeTransfer? {
    if (internalFeeTransfer == null) {
        return null
    }
    return FeeTransfer(bigDecimalToString(internalFeeTransfer.volume)!!,
            internalFeeTransfer.fromWalletId,
            internalFeeTransfer.toWalletId,
            internalFeeTransfer.asset,
            bigDecimalToString(internalFeeTransfer.feeCoef),
            index)
}

private fun orderTimeInForce(internalTimeInForce: com.swisschain.matching.engine.daos.order.OrderTimeInForce?): OrderTimeInForce? {
    return when (internalTimeInForce) {
        com.swisschain.matching.engine.daos.order.OrderTimeInForce.GTC -> OrderTimeInForce.GTC
        com.swisschain.matching.engine.daos.order.OrderTimeInForce.GTD -> OrderTimeInForce.GTD
        com.swisschain.matching.engine.daos.order.OrderTimeInForce.IOC -> OrderTimeInForce.IOC
        com.swisschain.matching.engine.daos.order.OrderTimeInForce.FOK -> OrderTimeInForce.FOK
        null -> null
    }
}