package com.swisschain.matching.engine.order.process

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.order.process.common.OrderUtils
import com.swisschain.matching.engine.order.transaction.ExecutionContext
import com.swisschain.matching.engine.outgoing.messages.LimitOrderWithTrades
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class StopOrderBookProcessor(private val limitOrderProcessor: LimitOrderProcessor,
                             private val applicationSettingsHolder: ApplicationSettingsHolder,
                             private val uuidHolder: UUIDHolder) {

    fun checkAndExecuteStopLimitOrders(executionContext: ExecutionContext): List<ProcessedOrder> {
        val processedOrders = mutableListOf<ProcessedOrder>()
        var order = getStopOrderToExecute(executionContext)
        while (order != null) {
            processedOrders.add(processStopOrder(order, executionContext))
            order = getStopOrderToExecute(executionContext)
        }
        return processedOrders
    }

    private fun processStopOrder(order: LimitOrder, executionContext: ExecutionContext): ProcessedOrder {
        reduceReservedBalance(order, executionContext)
        val childLimitOrder = OrderUtils.createChildLimitOrder(order,
                uuidHolder.getNextValue(),
                uuidHolder.getNextValue(),
                executionContext.date)
        order.childOrderExternalId = childLimitOrder.externalId
        addOrderToReport(order, executionContext)
        executionContext.info("Created child limit order (${childLimitOrder.externalId}) based on stop order ${order.externalId}")
        return limitOrderProcessor.processOrder(childLimitOrder, executionContext)
    }

    private fun addOrderToReport(order: LimitOrder, executionContext: ExecutionContext) {
        if (applicationSettingsHolder.isTrustedClient(order.walletId)) {
            executionContext.addTrustedClientLimitOrderWithTrades(LimitOrderWithTrades(order))
        } else {
            executionContext.addClientLimitOrderWithTrades(LimitOrderWithTrades(order))
        }
    }

    private fun getStopOrderToExecute(executionContext: ExecutionContext): LimitOrder? {
        executionContext.assetPairsById.values.forEach { assetPair ->
            getStopOrderToExecuteByAssetPair(assetPair.brokerId, assetPair.symbol, executionContext)?.let { stopOrderToExecute ->
                return stopOrderToExecute
            }
        }
        return null
    }

    private fun getStopOrderToExecuteByAssetPair(brokerId: String, assetPairId: String, executionContext: ExecutionContext): LimitOrder? {
        val orderBook = executionContext.orderBooksHolder.getChangedCopyOrOriginalOrderBook(brokerId, assetPairId)
        val bestBidPrice = orderBook.getBidPrice()
        val bestAskPrice = orderBook.getAskPrice()
        val order = executionContext.stopOrderBooksHolder.pollStopOrderToExecute(brokerId,
                assetPairId,
                bestBidPrice,
                bestAskPrice,
                executionContext.date)
        if (order != null) {
            executionContext.info("Process stop order ${order.externalId}, client ${order.walletId}, asset pair $assetPairId" +
                    " (bestBidPrice=$bestBidPrice, bestAskPrice=$bestAskPrice) due to message ${executionContext.messageId}")
        }
        return order
    }

    private fun reduceReservedBalance(order: LimitOrder, executionContext: ExecutionContext) {
        val reservedVolume = order.reservedLimitVolume!!
        order.reservedLimitVolume = null
        if (applicationSettingsHolder.isTrustedClient(order.walletId)) {
            return
        }
        val assetPair = executionContext.assetPairsById[order.assetPairId]!!
        val limitAssetId = if (order.isBuySide()) assetPair.quotingAssetId else assetPair.baseAssetId
        val walletOperation = WalletOperation(order.brokerId,
                order.walletId,
                limitAssetId,
                BigDecimal.ZERO,
                -reservedVolume)
        executionContext.walletOperationsProcessor.preProcess(listOf(walletOperation), true)
    }

}