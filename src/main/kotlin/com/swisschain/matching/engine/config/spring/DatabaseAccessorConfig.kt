package com.swisschain.matching.engine.config.spring

import com.swisschain.matching.engine.database.*
import com.swisschain.matching.engine.database.common.PersistenceManagerFactory
import com.swisschain.matching.engine.database.grpc.GrpcDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisCashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisProcessedMessagesDatabaseAccessor
import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import com.swisschain.matching.engine.database.stub.StubMonitoringDatabaseAccessor
import com.swisschain.matching.engine.database.stub.StubReservedVolumeCorrectionDatabaseAccessor
import com.swisschain.matching.engine.database.stub.StubSettingsDatabaseAccessor
import com.swisschain.matching.engine.database.stub.StubSettingsHistoryDatabaseAccessor
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.*

@Configuration
open class DatabaseAccessorConfig {

    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var persistenceManagerFactory: PersistenceManagerFactory

    //<editor-fold desc="Persistence managers">
    @Bean
    open fun persistenceManager(persistenceRedisConnection: Optional<RedisConnection>): PersistenceManager {
        return persistenceManagerFactory.get(persistenceRedisConnection)
    }

    @Bean
    open fun cashInOutOperationPreprocessorPersistenceManager(cashInOutOperationPreprocessorRedisConnection: Optional<RedisConnection>): PersistenceManager {
        return persistenceManagerFactory.get(cashInOutOperationPreprocessorRedisConnection)
    }


    @Bean
    open fun limitOrderCancelOperationPreprocessorPersistenceManager(limitOrderCancelOperationPreprocessorRedisConnection: Optional<RedisConnection>): PersistenceManager {
        return persistenceManagerFactory.get(limitOrderCancelOperationPreprocessorRedisConnection)

    }

    @Bean
    open fun cashTransferPreprocessorPersistenceManager(cashTransferOperationsPreprocessorRedisConnection: Optional<RedisConnection>): PersistenceManager {
        return persistenceManagerFactory.get(cashTransferOperationsPreprocessorRedisConnection)
    }
    //</editor-fold>

    @Bean
    open fun readOnlyProcessedMessagesDatabaseAccessor(redisProcessedMessagesDatabaseAccessor: Optional<RedisProcessedMessagesDatabaseAccessor>): ReadOnlyProcessedMessagesDatabaseAccessor {
        return redisProcessedMessagesDatabaseAccessor.get()
    }

    @Bean
    open fun cashOperationIdDatabaseAccessor(redisCashOperationIdDatabaseAccessor: Optional<RedisCashOperationIdDatabaseAccessor>): CashOperationIdDatabaseAccessor {
        return redisCashOperationIdDatabaseAccessor.get()
    }

    @Bean
    open fun messageSequenceNumberDatabaseAccessor(redisMessageSequenceNumberDatabaseAccessor: Optional<RedisMessageSequenceNumberDatabaseAccessor>): ReadOnlyMessageSequenceNumberDatabaseAccessor {
        return redisMessageSequenceNumberDatabaseAccessor.get()
    }
    //</editor-fold>

    //<editor-fold desc="gRPC DB accessors">

    @Bean
    open fun grpcReservedVolumesDatabaseAccessor(): ReservedVolumesDatabaseAccessor {
        return StubReservedVolumeCorrectionDatabaseAccessor()
    }

    @Bean
    open fun grpcSettingsDatabaseAccessor(): SettingsDatabaseAccessor {
        return StubSettingsDatabaseAccessor()
    }

    @Bean
    open fun settingsHistoryDatabaseAccessor(): SettingsHistoryDatabaseAccessor {
        return StubSettingsHistoryDatabaseAccessor()
    }

    @Bean
    @Profile("default")
    open fun grpcMonitoringDatabaseAccessor(): MonitoringDatabaseAccessor {
        return StubMonitoringDatabaseAccessor()
    }

    @Bean
    open fun grpcDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
        return GrpcDictionariesDatabaseAccessor(config.me.grpcEndpoints.dictionariesConnection)
    }
    //</editor-fold>
}