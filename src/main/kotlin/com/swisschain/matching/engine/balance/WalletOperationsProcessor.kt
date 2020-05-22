package com.swisschain.matching.engine.balance

import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.daos.wallet.Wallet
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.common.entity.BalancesData
import com.swisschain.matching.engine.database.common.entity.OrderBooksPersistenceData
import com.swisschain.matching.engine.database.common.entity.PersistenceData
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.order.transaction.CurrentTransactionBalancesHolder
import com.swisschain.matching.engine.order.transaction.WalletAssetBalance
import com.swisschain.matching.engine.outgoing.messages.ClientBalanceUpdate
import com.swisschain.matching.engine.utils.NumberUtils
import com.swisschain.utils.logging.MetricsLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class WalletOperationsProcessor(private val balancesHolder: BalancesHolder,
                                private val currentTransactionBalancesHolder: CurrentTransactionBalancesHolder,
                                private val applicationSettingsHolder: ApplicationSettingsHolder,
                                private val persistenceManager: PersistenceManager,
                                private val assetsHolder: AssetsHolder,
                                private val logger: Logger?): BalancesGetter {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(WalletOperationsProcessor::class.java.name)
        private val METRICS_LOGGER = MetricsLogger.getLogger()
    }

   private val clientBalanceUpdatesByWalletIdAndAssetId = HashMap<String, ClientBalanceUpdate>()

    fun preProcess(operations: Collection<WalletOperation>,
                   allowInvalidBalance: Boolean = false,
                   allowTrustedClientReservedBalanceOperation: Boolean = false): WalletOperationsProcessor {
        if (operations.isEmpty()) {
            return this
        }
        val changedAssetBalances = HashMap<String, ChangedAssetBalance>()
        operations.forEach { operation ->
            if (!allowTrustedClientReservedBalanceOperation && isTrustedClientReservedBalanceOperation(operation)) {
                return@forEach
            }
            val changedAssetBalance = changedAssetBalances.getOrPut(generateKey(operation)) {
                getChangedAssetBalance(operation.brokerId, operation.walletId, operation.assetId)
            }

            val asset = assetsHolder.getAsset(operation.brokerId, operation.assetId)
            changedAssetBalance.balance = NumberUtils.setScaleRoundHalfUp(changedAssetBalance.balance + operation.amount, asset.accuracy)

            changedAssetBalance.reserved = if (allowTrustedClientReservedBalanceOperation || !applicationSettingsHolder.isTrustedClient(operation.walletId))
                NumberUtils.setScaleRoundHalfUp(changedAssetBalance.reserved + operation.reservedAmount, asset.accuracy)
            else
                changedAssetBalance.reserved
        }

        try {
            changedAssetBalances.values.forEach { validateBalanceChange(it) }
        } catch (e: BalanceException) {
            if (!allowInvalidBalance) {
                throw e
            }
            val message = "Force applying of invalid balance: ${e.message}"
            (logger ?: LOGGER).error(message)
            METRICS_LOGGER.logError(message, e)
        }

        changedAssetBalances.forEach { processChangedAssetBalance(it.value) }
        return this
    }

    private fun processChangedAssetBalance(changedAssetBalance: ChangedAssetBalance) {
        if (!changedAssetBalance.isChanged()) {
            return
        }
        changedAssetBalance.apply()
        generateEventData(changedAssetBalance)
    }

    private fun generateEventData(changedAssetBalance: ChangedAssetBalance) {
        val key = generateKey(changedAssetBalance)
        val update = clientBalanceUpdatesByWalletIdAndAssetId.getOrPut(key) {
            ClientBalanceUpdate(
                    changedAssetBalance.brokerId,
                    changedAssetBalance.walletId,
                    changedAssetBalance.assetId,
                    changedAssetBalance.originBalance,
                    changedAssetBalance.balance,
                    changedAssetBalance.originReserved,
                    changedAssetBalance.reserved)
        }
        update.newBalance = changedAssetBalance.balance
        update.newReserved = changedAssetBalance.reserved
        if (isBalanceUpdateNotificationNotNeeded(update)) {
            clientBalanceUpdatesByWalletIdAndAssetId.remove(key)
        }
    }

    private fun isBalanceUpdateNotificationNotNeeded(clientBalanceUpdate: ClientBalanceUpdate): Boolean {
        return NumberUtils.equalsIgnoreScale(clientBalanceUpdate.oldBalance, clientBalanceUpdate.newBalance) &&
                NumberUtils.equalsIgnoreScale(clientBalanceUpdate.oldReserved, clientBalanceUpdate.newReserved)
    }

    fun apply(): WalletOperationsProcessor {
        currentTransactionBalancesHolder.apply()
        return this
    }

    fun persistenceData(): BalancesData {
        return currentTransactionBalancesHolder.persistenceData()
    }

    fun persistBalances(processedMessage: ProcessedMessage?,
                        orderBooksData: OrderBooksPersistenceData?,
                        stopOrderBooksData: OrderBooksPersistenceData?,
                        messageSequenceNumber: Long?): Boolean {
        return persistenceManager.persist(PersistenceData(persistenceData(),
                processedMessage,
                orderBooksData,
                stopOrderBooksData,
                messageSequenceNumber))
    }

    fun getClientBalanceUpdates(): List<ClientBalanceUpdate> {
        return clientBalanceUpdatesByWalletIdAndAssetId.values.toList()
    }

    override fun getAvailableBalance(brokerId: String, walletId: Long, assetId: String): BigDecimal {
        val balance = getChangedCopyOrOriginalAssetBalance(brokerId, walletId, assetId)
        return if (balance.reserved > BigDecimal.ZERO)
            balance.balance - balance.reserved
        else
            balance.balance
    }

    override fun getAvailableReservedBalance(brokerId: String, walletId: Long, assetId: String): BigDecimal {
        val balance = getChangedCopyOrOriginalAssetBalance(brokerId, walletId, assetId)
        return if (balance.reserved > BigDecimal.ZERO && balance.reserved < balance.balance)
            balance.reserved
        else
            balance.balance
    }

    override fun getReservedBalance(brokerId: String, walletId: Long, assetId: String): BigDecimal {
        return getChangedCopyOrOriginalAssetBalance(brokerId, walletId, assetId).reserved
    }

    private fun isTrustedClientReservedBalanceOperation(operation: WalletOperation): Boolean {
        return NumberUtils.equalsIgnoreScale(BigDecimal.ZERO, operation.amount) && applicationSettingsHolder.isTrustedClient(operation.walletId)
    }

    private fun getChangedAssetBalance(brokerId: String, walletId: Long, assetId: String): ChangedAssetBalance {
        val walletAssetBalance = getCurrentTransactionWalletAssetBalance(brokerId, walletId, assetId)
        return ChangedAssetBalance(brokerId, walletAssetBalance.wallet, walletAssetBalance.assetBalance)
    }

    private fun getChangedCopyOrOriginalAssetBalance(brokerId: String, walletId: Long, assetId: String): AssetBalance {
        return currentTransactionBalancesHolder.getChangedCopyOrOriginalAssetBalance(brokerId, walletId, assetId)
    }

    private fun getCurrentTransactionWalletAssetBalance(brokerId: String, walletId: Long, assetId: String): WalletAssetBalance {
        return currentTransactionBalancesHolder.getWalletAssetBalance(brokerId, walletId, assetId)
    }
}

private class ChangedAssetBalance(val brokerId: String,
                                  private val wallet: Wallet,
                                  assetBalance: AssetBalance) {

    val assetId = assetBalance.asset
    val walletId = wallet.walletId
    val originBalance = assetBalance.balance
    val originReserved = assetBalance.reserved
    var balance = originBalance
    var reserved = originReserved

    fun isChanged(): Boolean {
        return !NumberUtils.equalsIgnoreScale(originBalance, balance) ||
                !NumberUtils.equalsIgnoreScale(originReserved, reserved)
    }

    fun apply(): Wallet {
        wallet.setBalance(assetId, balance)
        wallet.setReservedBalance(assetId, reserved)
        return wallet
    }
}

private fun generateKey(operation: WalletOperation) = generateKey(operation.walletId, operation.assetId)

private fun generateKey(assetBalance: ChangedAssetBalance) = generateKey(assetBalance.walletId, assetBalance.assetId)

private fun generateKey(walletId: Long, assetId: String) = "${walletId}_$assetId"

@Throws(BalanceException::class)
private fun validateBalanceChange(assetBalance: ChangedAssetBalance) =
        validateBalanceChange(assetBalance.walletId,
                assetBalance.assetId,
                assetBalance.originBalance,
                assetBalance.originReserved,
                assetBalance.balance,
                assetBalance.reserved)

@Throws(BalanceException::class)
fun validateBalanceChange(walletId: Long, assetId: String, oldBalance: BigDecimal, oldReserved: BigDecimal, newBalance: BigDecimal, newReserved: BigDecimal) {
    val balanceInfo = "Invalid balance (client=$walletId, asset=$assetId, oldBalance=$oldBalance, oldReserved=$oldReserved, newBalance=$newBalance, newReserved=$newReserved)"

    // Balance can become negative earlier due to transfer operation with overdraftLimit > 0.
    // In this case need to check only difference of reserved & main balance.
    // It shouldn't be greater than previous one.
    if (newBalance < BigDecimal.ZERO && !(oldBalance < BigDecimal.ZERO && (oldBalance >= newBalance || oldReserved + newBalance >= newReserved + oldBalance))) {
        throw BalanceException(balanceInfo)
    }
    if (newReserved < BigDecimal.ZERO && oldReserved > newReserved) {
        throw BalanceException(balanceInfo)
    }

    // equals newBalance < newReserved && oldReserved - oldBalance < newReserved - newBalance
    if (newBalance < newReserved && oldReserved + newBalance < newReserved + oldBalance) {
        throw BalanceException(balanceInfo)
    }
}