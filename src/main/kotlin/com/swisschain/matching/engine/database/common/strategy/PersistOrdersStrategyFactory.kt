package com.swisschain.matching.engine.database.common.strategy

import com.swisschain.matching.engine.holders.OrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.holders.StopOrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PersistOrdersStrategyFactory() : FactoryBean<PersistOrdersDuringRedisTransactionStrategy> {

    @Autowired
    private lateinit var config: Config

    @Autowired
    private lateinit var ordersDatabaseAccessorsHolder: OrdersDatabaseAccessorsHolder

    @Autowired
    private lateinit var stopOrdersDatabaseAccessorsHolder: StopOrdersDatabaseAccessorsHolder

    override fun getObjectType(): Class<*>? {
        return PersistOrdersDuringRedisTransactionStrategy::class.java
    }

    override fun getObject(): PersistOrdersDuringRedisTransactionStrategy? {
        return RedisPersistOrdersStrategy(ordersDatabaseAccessorsHolder, stopOrdersDatabaseAccessorsHolder, config)
    }
}