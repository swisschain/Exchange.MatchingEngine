package com.swisschain.matching.engine.holders

import com.swisschain.matching.engine.database.redis.accessor.impl.RedisOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class OrdersDatabaseAccessorsHolderFactory : FactoryBean<OrdersDatabaseAccessorsHolder> {

    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var initialLoadingRedisConnection: Optional<RedisConnection>

    override fun getObjectType(): Class<*> {
        return OrdersDatabaseAccessorsHolder::class.java
    }

    override fun getObject(): OrdersDatabaseAccessorsHolder {
        return OrdersDatabaseAccessorsHolder(RedisOrderBookDatabaseAccessor(initialLoadingRedisConnection.get(), config.me.redis.ordersDatabase), null)
    }
}