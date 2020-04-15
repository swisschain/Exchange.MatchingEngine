package com.swisschain.matching.engine.config.spring

import com.swisschain.matching.engine.database.CashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.CashOperationsDatabaseAccessor
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.MonitoringDatabaseAccessor
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.ReadOnlyMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.ReadOnlyProcessedMessagesDatabaseAccessor
import com.swisschain.matching.engine.database.ReservedVolumesDatabaseAccessor
import com.swisschain.matching.engine.database.SettingsDatabaseAccessor
import com.swisschain.matching.engine.database.SettingsHistoryDatabaseAccessor
import com.swisschain.matching.engine.database.common.PersistenceManagerFactory
import com.swisschain.matching.engine.database.grpc.GrpcCashOperationsDatabaseAccessor
import com.swisschain.matching.engine.database.grpc.GrpcDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.grpc.GrpcMonitoringDatabaseAccessor
import com.swisschain.matching.engine.database.grpc.GrpcReservedVolumeCorrectionDatabaseAccessor
import com.swisschain.matching.engine.database.grpc.GrpcSettingsDatabaseAccessor
import com.swisschain.matching.engine.database.grpc.GrpcSettingsHistoryDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisCashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisProcessedMessagesDatabaseAccessor
import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.Optional

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
    open fun grpcCashOperationsDatabaseAccessor(): CashOperationsDatabaseAccessor {
        return GrpcCashOperationsDatabaseAccessor(config.me.grpcEndpoints.cashOperationsConnection)
    }
    @Bean
    open fun grpcReservedVolumesDatabaseAccessor(): ReservedVolumesDatabaseAccessor {
        return GrpcReservedVolumeCorrectionDatabaseAccessor(config.me.grpcEndpoints.reservedVolumesConnection)
    }

    @Bean
    open fun grpcSettingsDatabaseAccessor(): SettingsDatabaseAccessor {
        return GrpcSettingsDatabaseAccessor(config.me.grpcEndpoints.settingsConnection)
    }

    @Bean
    open fun settingsHistoryDatabaseAccessor(): SettingsHistoryDatabaseAccessor {
        return GrpcSettingsHistoryDatabaseAccessor(config.me.grpcEndpoints.settingsHistoryConnection )
    }

    @Bean
    @Profile("default")
    open fun grpcMonitoringDatabaseAccessor(): MonitoringDatabaseAccessor {
        return GrpcMonitoringDatabaseAccessor(config.me.grpcEndpoints.monitoringConnection)
    }

    @Bean
    open fun grpcDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
        return GrpcDictionariesDatabaseAccessor(config.me.grpcEndpoints.dictionariesConnection)
    }
    //</editor-fold>
}