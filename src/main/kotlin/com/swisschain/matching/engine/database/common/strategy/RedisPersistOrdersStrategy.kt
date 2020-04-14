package com.swisschain.matching.engine.database.common.strategy

import com.swisschain.matching.engine.database.common.entity.OrderBooksPersistenceData
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisStopOrderBookDatabaseAccessor
import com.swisschain.matching.engine.holders.OrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.holders.StopOrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.utils.config.Config
import redis.clients.jedis.Transaction

class RedisPersistOrdersStrategy(private val ordersDatabaseAccessorsHolder: OrdersDatabaseAccessorsHolder,
                                 private val stopOrdersDatabaseAccessorsHolder: StopOrdersDatabaseAccessorsHolder,
                                 private val config: Config) : PersistOrdersDuringRedisTransactionStrategy {
    override fun isRedisTransactionUsed(): Boolean {
        return true
    }

    override fun persist(transaction: Transaction,
                         orderBooksData: OrderBooksPersistenceData?,
                         stopOrderBooksData: OrderBooksPersistenceData?) {
        orderBooksData?.let {
            persistOrders(transaction, it)
        }
        stopOrderBooksData?.let {
            persistStopOrders(transaction, it)
        }
    }

    private fun persistOrders(transaction: Transaction, data: OrderBooksPersistenceData) {
        val primaryAccessor = ordersDatabaseAccessorsHolder.primaryAccessor as RedisOrderBookDatabaseAccessor
        if (data.ordersToSave.isEmpty() && data.ordersToRemove.isEmpty()) {
            return
        }
        transaction.select(config.me.redis.ordersDatabase)
        primaryAccessor.updateOrders(transaction, data.ordersToSave, data.ordersToRemove)
    }

    private fun persistStopOrders(transaction: Transaction, data: OrderBooksPersistenceData) {
        val primaryStopOrdersAccessor = stopOrdersDatabaseAccessorsHolder.primaryAccessor as RedisStopOrderBookDatabaseAccessor
        if (data.ordersToSave.isEmpty() && data.ordersToRemove.isEmpty()) {
            return
        }
        transaction.select(config.me.redis.ordersDatabase)
        primaryStopOrdersAccessor.updateOrders(transaction, data.ordersToSave, data.ordersToRemove)
    }
}