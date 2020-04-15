package com.swisschain.matching.engine.order.process.common

import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.order.ExecutionDataApplyService
import com.swisschain.matching.engine.order.process.StopOrderBookProcessor
import com.swisschain.matching.engine.order.transaction.ExecutionContextFactory
import com.swisschain.matching.engine.utils.plus
import org.springframework.stereotype.Component
import java.util.Date

@Component
class LimitOrdersCancelExecutorImpl(private val assetsPairsHolder: AssetsPairsHolder,
                                    private val executionContextFactory: ExecutionContextFactory,
                                    private val limitOrdersCanceller: LimitOrdersCanceller,
                                    private val stopOrderBookProcessor: StopOrderBookProcessor,
                                    private val executionDataApplyService: ExecutionDataApplyService) : LimitOrdersCancelExecutor {

    override fun cancelOrdersAndApply(request: CancelRequest): Boolean {
        with(request) {
            val executionContext = executionContextFactory.create(messageId,
                    requestId,
                    messageType,
                    processedMessage,
                    createAssetPairsByIdMapForOrders(plus(limitOrders, stopLimitOrders)),
                    Date(),
                    logger)

            limitOrdersCanceller.cancelOrders(limitOrders,
                    emptyList(),
                    stopLimitOrders,
                    emptyList(),
                    executionContext)

            stopOrderBookProcessor.checkAndExecuteStopLimitOrders(executionContext)

            return executionDataApplyService.persistAndSendEvents(messageWrapper, executionContext)
        }
    }

    private fun createAssetPairsByIdMapForOrders(orders: Collection<LimitOrder>): Map<String, AssetPair> {
        return orders.asSequence()
                .map { Pair(it.brokerId, it.assetPairId) }
                .toSet()
                .mapNotNull { assetsPairsHolder.getAssetPairAllowNulls(it.first, it.second) }
                .groupBy { it.symbol }
                .mapValues { it.value.single() }
    }
}