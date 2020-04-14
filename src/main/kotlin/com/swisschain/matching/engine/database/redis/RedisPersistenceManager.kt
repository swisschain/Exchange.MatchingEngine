package com.swisschain.matching.engine.database.redis

import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.common.entity.PersistenceData
import com.swisschain.matching.engine.database.common.strategy.PersistOrdersDuringRedisTransactionStrategy
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisCashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisProcessedMessagesDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisWalletDatabaseAccessor
import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.holders.CurrentTransactionDataHolder
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.performance.PerformanceStatsHolder
import com.swisschain.matching.engine.utils.PrintUtils
import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.utils.logging.MetricsLogger
import org.slf4j.LoggerFactory
import org.springframework.util.CollectionUtils
import redis.clients.jedis.Transaction
import redis.clients.jedis.exceptions.JedisException

class RedisPersistenceManager(
        private val primaryBalancesAccessor: RedisWalletDatabaseAccessor,
        private val redisProcessedMessagesDatabaseAccessor: RedisProcessedMessagesDatabaseAccessor,
        private val redisProcessedCashOperationIdDatabaseAccessor: RedisCashOperationIdDatabaseAccessor,
        private val persistOrdersStrategy: PersistOrdersDuringRedisTransactionStrategy,
        private val redisMessageSequenceNumberDatabaseAccessor: RedisMessageSequenceNumberDatabaseAccessor,
        private val redisConnection: RedisConnection,
        private val config: Config,
        private val currentTransactionDataHolder: CurrentTransactionDataHolder,
        private val performanceStatsHolder: PerformanceStatsHolder) : PersistenceManager {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RedisPersistenceManager::class.java.name)
        private val REDIS_PERFORMANCE_LOGGER = LoggerFactory.getLogger("${RedisPersistenceManager::class.java.name}.redis")
        private val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    override fun persist(data: PersistenceData): Boolean {
        if (data.isEmpty()) {
            return true
        }
        return try {
            persistData(redisConnection, data)
            true
        } catch (e: Exception) {
            val message = "Unable to save data (${data.getSummary()})"
            LOGGER.error(message, e)
            METRICS_LOGGER.logError(message, e)
            false
        }
    }

    private fun persistData(redisConnection: RedisConnection, data: PersistenceData) {
        val startTime = System.nanoTime()
        redisConnection.transactionalResource { transaction ->
            persistBalances(transaction, data.balancesData?.balances)
            persistProcessedMessages(transaction, data.processedMessage)

            if (data.processedMessage?.type == MessageType.CASH_IN_OUT_OPERATION.type ||
                    data.processedMessage?.type == MessageType.CASH_TRANSFER_OPERATION.type) {
                persistProcessedCashMessage(transaction, data.processedMessage)
            }

            val startPersistOrders = System.nanoTime()
            persistOrders(transaction, data)
            val endPersistOrders = System.nanoTime()

            persistMessageSequenceNumber(transaction, data.messageSequenceNumber)

            val persistTime = System.nanoTime()

            transaction.exec()
            val commitTime = System.nanoTime()
            val nonRedisOrdersPersistTime = if (persistOrdersStrategy.isRedisTransactionUsed()) 0 else endPersistOrders - startPersistOrders
            val messageId = data.processedMessage?.messageId
            REDIS_PERFORMANCE_LOGGER.debug("Total: ${PrintUtils.convertToString2((commitTime - startTime - nonRedisOrdersPersistTime).toDouble())}" +
                    ", persist: ${PrintUtils.convertToString2((persistTime - startTime - nonRedisOrdersPersistTime).toDouble())}" +
                    (if(nonRedisOrdersPersistTime != 0L) ", non redis orders persist time: ${PrintUtils.convertToString2(nonRedisOrdersPersistTime.toDouble())}" else "") +
                    ", commit: ${PrintUtils.convertToString2((commitTime - persistTime).toDouble())}" +
                    ", persisted data summary: ${data.getSummary()}" +
                    (if (messageId != null) ", messageId: ($messageId)" else ""))

            currentTransactionDataHolder.getMessageType()?.let {
                performanceStatsHolder.addPersistTime(it.type, commitTime - startTime)
            }
        }
    }

    private fun persistOrders(transaction: Transaction, data: PersistenceData) {
        try {
            persistOrdersStrategy.persist(transaction, data.orderBooksData, data.stopOrderBooksData)
        } catch (e: JedisException) {
            throw e
        } catch (e: Exception) {
            transaction.discard()
            throw e
        }
    }

    private fun persistProcessedMessages(transaction: Transaction, processedMessage: ProcessedMessage?) {
        LOGGER.trace("Start to persist processed messages in redis")

        if (processedMessage == null) {
            LOGGER.trace("Processed message is empty, skip persisting")
            return
        }

        redisProcessedMessagesDatabaseAccessor.save(transaction, processedMessage)
    }

    private fun persistProcessedCashMessage(transaction: Transaction, processedMessage: ProcessedMessage) {
        LOGGER.trace("Start to persist processed cash messages in redis")
        redisProcessedCashOperationIdDatabaseAccessor.save(transaction, processedMessage)
    }

    private fun persistBalances(transaction: Transaction, assetBalances: Collection<AssetBalance>?) {
        if (CollectionUtils.isEmpty(assetBalances)) {
            return
        }

        LOGGER.trace("Start to persist balances in redis")
        transaction.select(config.me.redis.balanceDatabase)
        primaryBalancesAccessor.insertOrUpdateBalances(transaction, assetBalances!!)
    }

    private fun persistMessageSequenceNumber(transaction: Transaction, sequenceNumber: Long?) {
        if (sequenceNumber == null) {
            return
        }
        redisMessageSequenceNumberDatabaseAccessor.save(transaction, sequenceNumber)
    }
}