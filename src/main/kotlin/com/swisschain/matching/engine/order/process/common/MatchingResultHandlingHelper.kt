package com.swisschain.matching.engine.order.process.common

import com.swisschain.matching.engine.balance.BalanceException
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.order.process.context.OrderExecutionContext
import com.swisschain.matching.engine.outgoing.messages.LimitOrderWithTrades
import com.swisschain.matching.engine.utils.NumberUtils
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class MatchingResultHandlingHelper(private val applicationSettingsHolder: ApplicationSettingsHolder) {

    fun preProcessCancelledOppositeOrders(orderExecutionContext: OrderExecutionContext<*>) {
        val date = orderExecutionContext.executionContext.date
        val clientsLimitOrders = mutableListOf<LimitOrder>()
        val trustedClientsLimitOrders = mutableListOf<LimitOrder>()
        val walletOperations = mutableListOf<WalletOperation>()
        orderExecutionContext.matchingResult!!.cancelledLimitOrders.forEach { orderCopyWrapper ->
            val changedOrder = orderCopyWrapper.copy
            changedOrder.updateStatus(OrderStatus.Cancelled, date)
            walletOperations.add(createCancelledOppositeOrderWalletOperation(orderExecutionContext, changedOrder))
            if (isOrderForTrustedClientsReport(changedOrder)) {
                trustedClientsLimitOrders.add(changedOrder)
            } else {
                clientsLimitOrders.add(changedOrder)
            }
        }
        orderExecutionContext.cancelledOppositeClientsOrders = clientsLimitOrders
        orderExecutionContext.cancelledOppositeTrustedClientsOrders = trustedClientsLimitOrders
        orderExecutionContext.cancelledOppositeOrdersWalletOperations = walletOperations
    }

    fun preProcessUncompletedOppositeOrder(orderExecutionContext: OrderExecutionContext<*>) {
        val matchingResult = orderExecutionContext.matchingResult!!
        val uncompletedLimitOrderCopy = matchingResult.uncompletedLimitOrderCopy!!
        val assetPair = orderExecutionContext.executionContext.assetPairsById[uncompletedLimitOrderCopy.assetPairId]!!
        if (uncompletedLimitOrderCopy.getAbsRemainingVolume() >= assetPair.minVolume) {
            orderExecutionContext.isUncompletedOrderCancelled = false
            return
        }
        uncompletedLimitOrderCopy.updateStatus(OrderStatus.Cancelled, orderExecutionContext.executionContext.date)
        if (orderExecutionContext.cancelledOppositeOrdersWalletOperations == null) {
            orderExecutionContext.cancelledOppositeOrdersWalletOperations = mutableListOf()
        }
        orderExecutionContext.cancelledOppositeOrdersWalletOperations!!.add(createCancelledOppositeOrderWalletOperation(orderExecutionContext, uncompletedLimitOrderCopy))
        orderExecutionContext.executionContext.info("Opposite limit order (${uncompletedLimitOrderCopy.externalId} is cancelled due to min remaining volume" +
                "(${NumberUtils.roundForPrint(uncompletedLimitOrderCopy.getAbsRemainingVolume())} < ${NumberUtils.roundForPrint(assetPair.minVolume)})")
        orderExecutionContext.isUncompletedOrderCancelled = true
    }

    private fun createCancelledOppositeOrderWalletOperation(orderExecutionContext: OrderExecutionContext<*>, oppositeOrder: LimitOrder): WalletOperation {
        val assetId = orderExecutionContext.oppositeLimitAsset!!.symbol
        val reservedVolume = oppositeOrder.reservedLimitVolume
                ?: if (oppositeOrder.isBuySide()) oppositeOrder.getAbsRemainingVolume() * oppositeOrder.price else oppositeOrder.getAbsRemainingVolume()
        val reservedBalance = orderExecutionContext.executionContext.walletOperationsProcessor.getReservedBalance(oppositeOrder.brokerId, oppositeOrder.walletId, assetId)
        return WalletOperation(oppositeOrder.brokerId,
                oppositeOrder.accountId,
                oppositeOrder.walletId,
                assetId,
                BigDecimal.ZERO,
                if (reservedVolume > reservedBalance) -reservedBalance else -reservedVolume)
    }

    fun isOrderForTrustedClientsReport(order: LimitOrder): Boolean {
        return !order.isPartiallyMatched() && applicationSettingsHolder.isTrustedClient(order.walletId)
    }

    fun processCancelledOppositeOrders(orderExecutionContext: OrderExecutionContext<*>) {
        val originalCancelledLimitOrders = orderExecutionContext.matchingResult!!.cancelledLimitOrders.map { it.origin!! }
        orderExecutionContext.executionContext.orderBooksHolder.addCancelledOrders(originalCancelledLimitOrders)
        orderExecutionContext.executionContext.addClientsLimitOrdersWithTrades(orderExecutionContext.cancelledOppositeClientsOrders!!.map { LimitOrderWithTrades(it) })
        orderExecutionContext.executionContext.addTrustedClientsLimitOrdersWithTrades(orderExecutionContext.cancelledOppositeTrustedClientsOrders!!.map { LimitOrderWithTrades(it) })
    }

    fun processUncompletedOppositeOrder(orderExecutionContext: OrderExecutionContext<*>) {
        val uncompletedLimitOrder = orderExecutionContext.matchingResult!!.uncompletedLimitOrder!!
        if (orderExecutionContext.isUncompletedOrderCancelled) {
            orderExecutionContext.executionContext.orderBooksHolder.addCancelledOrders(listOf(uncompletedLimitOrder))
        } else {
            orderExecutionContext.matchingResult!!.orderBook.put(uncompletedLimitOrder)
        }
    }

    fun processWalletOperations(orderExecutionContext: OrderExecutionContext<*>) {
        orderExecutionContext.executionContext.walletOperationsProcessor
                .preProcess(orderExecutionContext.ownWalletOperations!!)
                .preProcess(orderExecutionContext.matchingResult!!.oppositeCashMovements, true)
        if (orderExecutionContext.cancelledOppositeOrdersWalletOperations != null) {
            preProcessCancelledOrdersWalletOperations(orderExecutionContext)
        }
    }

    fun preProcessCancelledOrdersWalletOperations(orderExecutionContext: OrderExecutionContext<*>) {
        try {
            orderExecutionContext.executionContext.walletOperationsProcessor.preProcess(orderExecutionContext.cancelledOppositeOrdersWalletOperations!!)
        } catch (e: BalanceException) {
            orderExecutionContext.executionContext.error("Unable to process cancelled orders wallet operations after matching: ${e.message}")
        }
    }
}