package com.swisschain.matching.engine.utils.balance

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.daos.balance.ClientOrdersReservedVolume
import com.swisschain.matching.engine.daos.balance.ReservedVolumeCorrection
import com.swisschain.matching.engine.daos.wallet.Wallet
import com.swisschain.matching.engine.database.ReservedVolumesDatabaseAccessor
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.holders.OrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.holders.StopOrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.outgoing.messages.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.ClientBalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.builders.EventFactory
import com.swisschain.matching.engine.outgoing.messages.v2.events.ReservedBalanceUpdateEvent
import com.swisschain.matching.engine.services.MessageSender
import com.swisschain.matching.engine.utils.NumberUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Date
import java.util.HashMap
import java.util.LinkedList
import java.util.UUID

@Component
@Order(3)
class ReservedVolumesRecalculator @Autowired constructor(private val orderBookDatabaseAccessorHolder: OrdersDatabaseAccessorsHolder,
                                                         private val stopOrdersDatabaseAccessorsHolder: StopOrdersDatabaseAccessorsHolder,
                                                         private val reservedVolumesDatabaseAccessor: ReservedVolumesDatabaseAccessor,
                                                         private val assetsHolder: AssetsHolder,
                                                         private val assetsPairsHolder :AssetsPairsHolder,
                                                         private val balancesHolder: BalancesHolder,
                                                         private val applicationSettingsHolder: ApplicationSettingsHolder,
                                                         @Value("#{Config.me.correctReservedVolumes}") private val correctReservedVolumes: Boolean,
                                                         private val messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                                         private val messageSender: MessageSender) : ApplicationRunner {
    override fun run(args: ApplicationArguments?) {
        correctReservedVolumesIfNeed()
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ReservedVolumesRecalculator::class.java.name)

        fun teeLog(message: String) {
            println(message)
            LOGGER.info(message)
        }
    }

    fun correctReservedVolumesIfNeed() {
        if (!correctReservedVolumes) {
            return
        }

        teeLog("Starting order books analyze")
        recalculate()
    }

    fun recalculate() {

        val orders = orderBookDatabaseAccessorHolder.primaryAccessor.loadLimitOrders()
        val stopOrders = stopOrdersDatabaseAccessorsHolder.primaryAccessor.loadStopLimitOrders()

        val reservedBalances = HashMap<String, MutableMap<String, ClientOrdersReservedVolume>>()
        var count = 1
        val now = Date()

        val handleOrder: (order: LimitOrder, isStopOrder: Boolean) -> Unit = { order, isStopOrder->
            if (!applicationSettingsHolder.isTrustedClient(order.walletId)) {
                try {
                    if (isStopOrder) {
                        LOGGER.info("${count++} Client:${order.walletId}, id: ${order.externalId}, asset:${order.assetPairId}, lowerLimitPrice:${order.lowerLimitPrice}, lowerPrice:${order.lowerPrice}, upperLimitPrice:${order.upperLimitPrice}, upperPrice:${order.upperPrice}, volume:${order.volume}, date:${order.registered}, status:${order.status}, reserved: ${order.reservedLimitVolume}}")
                    } else {
                        LOGGER.info("${count++} Client:${order.walletId}, id: ${order.externalId}, asset:${order.assetPairId}, price:${order.price}, volume:${order.volume}, date:${order.registered}, status:${order.status}, reserved: ${order.reservedLimitVolume}}")
                    }
                    val assetPair = assetsPairsHolder.getAssetPair(order.brokerId, order.assetPairId)
                    val asset = assetsHolder.getAsset(order.brokerId, if (order.isBuySide()) assetPair.quotingAssetId else assetPair.baseAssetId)

                    val reservedVolume = if (order.reservedLimitVolume != null) {
                        order.reservedLimitVolume!!
                    } else {
                        val calculatedReservedVolume = if (order.isBuySide())
                            NumberUtils.setScale(if (isStopOrder) order.volume * (order.upperPrice ?: order.lowerPrice)!!
                            else order.getAbsRemainingVolume() * order.price, asset.accuracy, false)
                        else order.getAbsRemainingVolume()
                        LOGGER.info("Null reserved volume, recalculated: $calculatedReservedVolume")
                        calculatedReservedVolume
                    }

                    val clientAssets = reservedBalances.getOrPut(order.walletId) { HashMap() }
                    val balance = clientAssets.getOrPut(asset.assetId) { ClientOrdersReservedVolume() }
                    val newBalance = NumberUtils.setScaleRoundHalfUp(balance.volume + reservedVolume, asset.accuracy)
                    balance.volume = newBalance
                    balance.orderIds.add(order.externalId)
                } catch (e: Exception) {
                    val errorMessage = "Unable to handle order (id: ${order.externalId}): ${e.message}"
                    teeLog(errorMessage)
                }
            }
        }

        orders.forEach { handleOrder(it, false) }
        stopOrders.forEach { handleOrder(it, true) }

        LOGGER.info("---------------------------------------------------------------------------------------------------")

        reservedBalances.forEach { client ->
            LOGGER.info("${client.key} : ${client.value}")
        }

        LOGGER.info("---------------------------------------------------------------------------------------------------")

        val corrections = LinkedList<ReservedVolumeCorrection>()
        val updatedWallets = mutableSetOf<Wallet>()
        val balanceUpdates = mutableListOf<ClientBalanceUpdate>()
        balancesHolder.wallets.forEach {broker ->
            broker.value.values.forEach { wallet ->
                val id = wallet.walletId
                wallet.balances.values.forEach { assetBalance->
                    val oldBalance = assetBalance.reserved
                    val newBalance = reservedBalances[id]?.get(assetBalance.asset)
                    if (newBalance != null && newBalance.volume > BigDecimal.ZERO) {
                        if (!NumberUtils.equalsIgnoreScale(oldBalance, newBalance.volume)) {
                            val correction = ReservedVolumeCorrection(now, id, assetBalance.asset, newBalance.orderIds.joinToString(","), oldBalance, newBalance.volume)
                            corrections.add(correction)
                            teeLog("1 $id, ${assetBalance.asset} : Old $oldBalance New $newBalance")
                            wallet.setReservedBalance(assetBalance.asset, newBalance.volume)
                            updatedWallets.add(wallet)
                            val balanceUpdate = ClientBalanceUpdate(wallet.brokerId,
                                    id,
                                    assetBalance.asset,
                                    assetBalance.balance,
                                    assetBalance.balance,
                                    oldBalance,
                                    newBalance.volume)
                            balanceUpdates.add(balanceUpdate)
                        }
                    } else if (!NumberUtils.equalsIgnoreScale(oldBalance, BigDecimal.ZERO)) {
                        val orderIds = newBalance?.orderIds?.joinToString(",")
                        val correction = ReservedVolumeCorrection(now, id, assetBalance.asset, orderIds, oldBalance, newBalance?.volume ?: BigDecimal.ZERO)
                        corrections.add(correction)
                        teeLog("2 $id, ${assetBalance.asset} : Old $oldBalance New ${newBalance ?: 0.0}")
                        wallet.setReservedBalance(assetBalance.asset, BigDecimal.ZERO)
                        updatedWallets.add(wallet)
                        val balanceUpdate = ClientBalanceUpdate(wallet.brokerId,
                                id,
                                assetBalance.asset,
                                assetBalance.balance,
                                assetBalance.balance,
                                oldBalance,
                                BigDecimal.ZERO)
                        balanceUpdates.add(balanceUpdate)
                    }
                }
            }
        }
        if (updatedWallets.isNotEmpty()) {
            val operationId = UUID.randomUUID().toString()
            LOGGER.info("Starting balances update, operationId: $operationId")

            var sequenceNumber: Long? = null
            val reservedBalanceUpdateEvents = mutableListOf<ReservedBalanceUpdateEvent>()
            balanceUpdates.forEach { clientBalanceUpdate ->
                sequenceNumber = messageSequenceNumberHolder.getNewValue()
                val walletOperation = WalletOperation(clientBalanceUpdate.brokerId,
                        clientBalanceUpdate.id,
                        clientBalanceUpdate.asset,
                        BigDecimal.ZERO,
                        clientBalanceUpdate.newReserved - clientBalanceUpdate.oldReserved
                )
                reservedBalanceUpdateEvents.add(EventFactory.createReservedBalanceUpdateEvent(sequenceNumber!!,
                        operationId,
                        operationId,
                        now,
                        MessageType.LIMIT_ORDER,
                        listOf(clientBalanceUpdate),
                        walletOperation))
            }

            balancesHolder.insertOrUpdateWallets(updatedWallets, sequenceNumber)
            reservedVolumesDatabaseAccessor.addCorrectionsInfo(corrections)
            LOGGER.info(BalanceUpdate(operationId, MessageType.LIMIT_ORDER.name, now, balanceUpdates, operationId).toString())
            reservedBalanceUpdateEvents.forEach { messageSender.sendMessage(it) }

        }
        teeLog("Reserved volume recalculation finished")
    }

}