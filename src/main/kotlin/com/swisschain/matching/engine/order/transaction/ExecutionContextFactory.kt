package com.swisschain.matching.engine.order.transaction

import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.AssetPair
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.services.GenericStopLimitOrderService
import com.swisschain.matching.engine.services.validators.impl.OrderValidationResult
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.util.Date

@Component
class ExecutionContextFactory(private val balancesHolder: BalancesHolder,
                              private val genericLimitOrderService: GenericLimitOrderService,
                              private val genericStopLimitOrderService: GenericStopLimitOrderService,
                              private val assetsHolder: AssetsHolder) {

    fun create(messageId: String,
               requestId: String,
               messageType: MessageType,
               processedMessage: ProcessedMessage?,
               assetPairsById: Map<String, AssetPair>,
               date: Date,
               logger: Logger,
               assetsById: Map<String, Asset> = getAssetsByIdMap(assetPairsById),
               preProcessorValidationResultsByOrderId: Map<String, OrderValidationResult> = emptyMap()): ExecutionContext {
        return ExecutionContext(messageId,
                requestId,
                messageType,
                processedMessage,
                assetPairsById,
                assetsById,
                preProcessorValidationResultsByOrderId,
                balancesHolder.createWalletProcessor(logger),
                genericLimitOrderService.createCurrentTransactionOrderBooksHolder(),
                genericStopLimitOrderService.createCurrentTransactionOrderBooksHolder(),
                date,
                logger)
    }

    private fun getAssetsByIdMap(assetPairsById: Map<String, AssetPair>): Map<String, Asset> {
        return assetPairsById.values
                .flatMapTo(mutableSetOf()) {
                    listOf(Pair(it.brokerId, it.baseAssetId), Pair(it.brokerId, it.quotingAssetId))
                }
                .asSequence()
                .map { assetsHolder.getAsset(it.first, it.second) }
                .groupBy { it.symbol }
                .mapValues { it.value.single() }
    }
}
