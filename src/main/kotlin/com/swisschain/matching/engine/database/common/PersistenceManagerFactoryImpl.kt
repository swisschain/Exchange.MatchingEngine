package com.swisschain.matching.engine.database.common

import com.swisschain.matching.engine.database.CashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.ReadOnlyMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.WalletDatabaseAccessor
import com.swisschain.matching.engine.database.common.strategy.PersistOrdersDuringRedisTransactionStrategy
import com.swisschain.matching.engine.database.redis.RedisPersistenceManager
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisCashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisProcessedMessagesDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisWalletDatabaseAccessor
import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import com.swisschain.matching.engine.holders.CurrentTransactionDataHolder
import com.swisschain.matching.engine.performance.PerformanceStatsHolder
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class PersistenceManagerFactoryImpl(private val walletDatabaseAccessor: WalletDatabaseAccessor,
                                    private val redisProcessedMessagesDatabaseAccessor: Optional<RedisProcessedMessagesDatabaseAccessor>,
                                    private val cashOperationIdDatabaseAccessor: Optional<CashOperationIdDatabaseAccessor>,
                                    private val messageSequenceNumberDatabaseAccessor: Optional<ReadOnlyMessageSequenceNumberDatabaseAccessor>,
                                    private val config: Config,
                                    private val currentTransactionDataHolder: CurrentTransactionDataHolder,
                                    private val performanceStatsHolder: PerformanceStatsHolder,
                                    private val persistOrdersStrategy: Optional<PersistOrdersDuringRedisTransactionStrategy>) : PersistenceManagerFactory {

    override fun get(redisConnection: Optional<RedisConnection>): PersistenceManager {
        return createRedisPersistenceManager(redisConnection.get())
    }

    private fun createRedisPersistenceManager(redisConnection: RedisConnection): RedisPersistenceManager {
        return RedisPersistenceManager(
                walletDatabaseAccessor as RedisWalletDatabaseAccessor,
                redisProcessedMessagesDatabaseAccessor.get(),
                cashOperationIdDatabaseAccessor.get() as RedisCashOperationIdDatabaseAccessor,
                persistOrdersStrategy.get(),
                messageSequenceNumberDatabaseAccessor.get() as RedisMessageSequenceNumberDatabaseAccessor,
                redisConnection,
                config,
                currentTransactionDataHolder,
                performanceStatsHolder
        )
    }
}