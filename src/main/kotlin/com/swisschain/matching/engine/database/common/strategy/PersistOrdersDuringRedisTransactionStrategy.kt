package com.swisschain.matching.engine.database.common.strategy

import com.swisschain.matching.engine.database.common.entity.OrderBooksPersistenceData
import redis.clients.jedis.Transaction

interface PersistOrdersDuringRedisTransactionStrategy {
    fun persist(transaction: Transaction,
                orderBooksData: OrderBooksPersistenceData?,
                stopOrderBooksData: OrderBooksPersistenceData?)
    fun isRedisTransactionUsed(): Boolean
}