package com.swisschain.matching.engine.holders

import com.swisschain.matching.engine.balance.BalancesGetter
import com.swisschain.matching.engine.balance.WalletOperationsProcessor
import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.daos.wallet.Wallet
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.WalletDatabaseAccessor
import com.swisschain.matching.engine.database.common.entity.BalancesData
import com.swisschain.matching.engine.database.common.entity.PersistenceData
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.order.transaction.CurrentTransactionBalancesHolder
import org.slf4j.Logger
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class BalancesHolder(private val walletDatabaseAccessor: WalletDatabaseAccessor,
                     private val persistenceManager: PersistenceManager,
                     private val assetsHolder: AssetsHolder,
                     private val applicationSettingsHolder: ApplicationSettingsHolder): BalancesGetter {

    lateinit var wallets: MutableMap<String, MutableMap<Long, Wallet>>
    var initialClientsCount = 0
    var initialBalancesCount = 0

    init {
        update()
    }

    private fun update() {
        wallets = walletDatabaseAccessor.loadWallets()
        initialClientsCount = wallets.values.sumBy { it.size }
        initialBalancesCount = wallets.values.sumBy { it.values.sumBy { it.balances.size } }
    }

    fun clientExists(brokerId:String, walletId: Long): Boolean {
        return wallets.containsKey(brokerId) && wallets[brokerId]!!.containsKey(walletId)
    }

    fun getBalances(brokerId:String, walletId: Long): Map<String, AssetBalance> {
        return wallets[brokerId]?.get(walletId)?.balances ?: emptyMap()
    }

    fun getBalance(brokerId:String, walletId: Long, assetId: String): BigDecimal {
        return getBalances(brokerId, walletId)[assetId]?.balance ?: BigDecimal.ZERO
    }

    override fun getReservedBalance(brokerId:String, walletId: Long, assetId: String): BigDecimal {
        return getBalances(brokerId, walletId)[assetId]?.reserved ?: BigDecimal.ZERO
    }

    override fun getAvailableBalance(brokerId:String, walletId: Long, assetId: String): BigDecimal {
        val wallet = wallets[brokerId]?.get(walletId)
        if (wallet != null) {
            val balance = wallet.balances[assetId]
            if (balance != null) {
                return (if (balance.reserved > BigDecimal.ZERO)
                    balance.balance - balance.reserved
                else balance.balance)
            }
        }

        return BigDecimal.ZERO
    }

    override fun getAvailableReservedBalance(brokerId:String, walletId: Long, assetId: String): BigDecimal {
        val wallet = wallets[brokerId]?.get(walletId)
        if (wallet != null) {
            val balance = wallet.balances[assetId]
            if (balance != null) {
                // reserved can be greater than base balance due to transfer with overdraft
                return if (balance.reserved.signum() == 1 && balance.reserved <= balance.balance) balance.reserved else balance.balance
            }
        }

        return BigDecimal.ZERO
    }

    fun updateBalance(processedMessage: ProcessedMessage?,
                      messageSequenceNumber: Long?,
                      brokerId:String,
                      walletId: Long,
                      assetId: String,
                      balance: BigDecimal): Boolean {
        val currentTransactionBalancesHolder = createCurrentTransactionBalancesHolder()
        currentTransactionBalancesHolder.updateBalance(brokerId, walletId, assetId, balance)
        val balancesData = currentTransactionBalancesHolder.persistenceData()
        val persisted = persistenceManager.persist(PersistenceData(balancesData, processedMessage, null, null, messageSequenceNumber))
        if (!persisted) {
            return false
        }
        currentTransactionBalancesHolder.apply()
        return true
    }

    fun updateReservedBalance(processedMessage: ProcessedMessage?,
                              messageSequenceNumber: Long?,
                              brokerId:String,
                              walletId: Long,
                              assetId: String,
                              balance: BigDecimal,
                              skipForTrustedClient: Boolean = true): Boolean {
        val currentTransactionBalancesHolder = createCurrentTransactionBalancesHolder()
        currentTransactionBalancesHolder.updateReservedBalance(brokerId, walletId, assetId, balance)
        val balancesData = currentTransactionBalancesHolder.persistenceData()
        val persisted = persistenceManager.persist(PersistenceData(balancesData, processedMessage, null, null, messageSequenceNumber))
        if (!persisted) {
            return false
        }
        currentTransactionBalancesHolder.apply()
        return true
    }

    fun insertOrUpdateWallets(wallets: Collection<Wallet>, messageSequenceNumber: Long?) {
        persistenceManager.persist(PersistenceData(BalancesData(wallets, wallets.flatMap { it.balances.values }), null, null, null,
                messageSequenceNumber = messageSequenceNumber))
        update()
    }

    fun isTrustedClient(walletId: Long) = applicationSettingsHolder.isTrustedClient(walletId)

    fun createWalletProcessor(logger: Logger?): WalletOperationsProcessor {
        return WalletOperationsProcessor(this,
                createCurrentTransactionBalancesHolder(),
                applicationSettingsHolder,
                persistenceManager,
                assetsHolder,
                logger)
    }

    private fun createCurrentTransactionBalancesHolder() = CurrentTransactionBalancesHolder(this)

    fun setWallets(wallets: Collection<Wallet>) {
        wallets.forEach { wallet ->
            this.wallets.getOrPut(wallet.brokerId) { HashMap() }[wallet.walletId] = wallet
        }
    }
}