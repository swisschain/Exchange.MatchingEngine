
package com.swisschain.matching.engine.config.spring

import com.swisschain.matching.engine.database.redis.accessor.impl.RedisCashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisProcessedMessagesDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisWalletDatabaseAccessor
import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import com.swisschain.matching.engine.database.redis.connection.RedisConnectionFactory
import com.swisschain.matching.engine.database.redis.connection.impl.RedisReconnectionManager
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler

@Configuration
open class RedisConfig {

    @Autowired
    private lateinit var redisConnectionFactory: RedisConnectionFactory

    @Autowired
    private lateinit var config: Config

    //<editor-fold desc="Redis connections">
    @Bean
    open fun pingRedisConnection(): RedisConnection? {
        return redisConnectionFactory.getConnection("pingRedisConnection")
    }

    @Bean
    open fun cashTransferOperationIdRedisConnection(): RedisConnection? {
        return redisConnectionFactory.getConnection("cashTransferOperationIdRedisConnection")
    }

    @Bean
    open fun cashInOutOperationIdRedisConnection(): RedisConnection? {
        return redisConnectionFactory.getConnection("cashInOutOperationIdRedisConnection")
    }

    @Bean
    open fun initialLoadingRedisConnection(): RedisConnection? {
        return redisConnectionFactory.getConnection("initialLoadingRedisConnection")
    }

    @Bean
    open fun persistenceRedisConnection(): RedisConnection? {
        return redisConnectionFactory.getConnection("persistenceRedisConnection")
    }

    @Bean
    open fun limitOrderCancelOperationPreprocessorRedisConnection(): RedisConnection? {
        return redisConnectionFactory.getConnection("limitOrderCancelOperationPreprocessorRedisConnection")
    }

    @Bean
    open fun cashInOutOperationPreprocessorRedisConnection(): RedisConnection? {
        return redisConnectionFactory.getConnection("cashInOutOperationPreprocessorRedisConnection")
    }


    @Bean
    open fun cashTransferOperationsPreprocessorRedisConnection(): RedisConnection? {
        return redisConnectionFactory.getConnection("cashTransferOperationsPreprocessorRedisConnection")
    }
    //</editor-fold>

    //<editor-fold desc="Redis database accessors">
    @Bean
    open fun redisProcessedMessagesDatabaseAccessor(): RedisProcessedMessagesDatabaseAccessor? {
        return RedisProcessedMessagesDatabaseAccessor(initialLoadingRedisConnection()!!,
                config.me.redis.processedMessageDatabase,
                getProcessedMessageTTL())
    }


    @Bean
    open fun redisWalletDatabaseAccessor(): RedisWalletDatabaseAccessor? {
        return RedisWalletDatabaseAccessor(initialLoadingRedisConnection()!!, config.me.redis.balanceDatabase)
    }

    @Bean
    open fun redisCashOperationIdDatabaseAccessor(): RedisCashOperationIdDatabaseAccessor? {
        return RedisCashOperationIdDatabaseAccessor(cashInOutOperationIdRedisConnection()!!,
                cashTransferOperationIdRedisConnection()!!,
                config.me.redis.processedCashMessageDatabase)
    }

    @Bean
    open fun redisMessageSequenceNumberDatabaseAccessor(): RedisMessageSequenceNumberDatabaseAccessor? {
        return RedisMessageSequenceNumberDatabaseAccessor(initialLoadingRedisConnection()!!,
                config.me.redis.sequenceNumberDatabase)
    }
    //</editor-fold>

    //<editor-fold desc="etc">
    @Bean
    open fun redisReconnectionManager(taskScheduler: TaskScheduler,
                                      applicationEventPublisher: ApplicationEventPublisher,
                                      allRedisConnections: List<RedisConnection>,
                                      @Value("\${redis.health.check.interval}") updateInterval: Long,
                                      @Value("\${redis.health.check.reconnect.interval}") reconnectInterval: Long): RedisReconnectionManager? {
        return RedisReconnectionManager(config.me, allRedisConnections, pingRedisConnection()!!,
                taskScheduler, applicationEventPublisher, updateInterval, reconnectInterval)
    }
    //</editor-fold>

    private fun getProcessedMessageTTL(): Int {
        return (config.me.processedMessagesInterval / 500).toInt()
    }
}