package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.outgoing.messages.LimitOrderWithTrades
import java.math.BigDecimal
import java.util.LinkedList

data class CancelledOrdersOperationsResult(
        val walletOperations: List<WalletOperation> = LinkedList(),
        val clientLimitOrderWithTrades: List<LimitOrderWithTrades> = LinkedList(),
        val trustedClientLimitOrderWithTrades: List<LimitOrderWithTrades> = LinkedList()
)

class WalletOperationsCalculator(
        private val assetsPairsHolder: AssetsPairsHolder,
        private val balancesHolder: BalancesHolder,
        private val applicationSettingsHolder: ApplicationSettingsHolder
) {

    fun calculateForCancelledOrders(orders: List<LimitOrder>): CancelledOrdersOperationsResult {
        val walletOperation = LinkedList<WalletOperation>()
        val trustedLimitOrderWithTrades = LinkedList<LimitOrderWithTrades>()
        val limitOrderWithTrades = LinkedList<LimitOrderWithTrades>()

        orders.forEach { order ->
            val isTrustedClientOrder = applicationSettingsHolder.isTrustedClient(order.walletId)

            if (!isTrustedClientOrder) {
                val assetPair = assetsPairsHolder.getAssetPair(order.brokerId, order.assetPairId)
                val limitAsset = if (order.isBuySide()) assetPair.quotingAssetId else assetPair.baseAssetId
                val limitVolume = order.reservedLimitVolume ?: if (order.isBuySide()) order.getAbsRemainingVolume() * order.price else order.getAbsRemainingVolume()
                val reservedBalance = balancesHolder.getReservedBalance(order.brokerId, order.walletId, limitAsset)

                if (reservedBalance > BigDecimal.ZERO) {
                    walletOperation.add(
                            WalletOperation(order.brokerId, order.walletId, limitAsset, BigDecimal.ZERO, if (limitVolume > reservedBalance) -reservedBalance else -limitVolume)
                    )
                }
            }

            if (isTrustedClientOrder && !order.isPartiallyMatched()) {
                limitOrderWithTrades.add(LimitOrderWithTrades(order))
            } else {
                trustedLimitOrderWithTrades.add(LimitOrderWithTrades(order))
            }
        }

        return CancelledOrdersOperationsResult(walletOperation, trustedLimitOrderWithTrades, limitOrderWithTrades)
    }

}