package com.swisschain.matching.engine.database.redis.accessor.impl

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.database.StopOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.redis.connection.RedisConnection

class RedisStopOrderBookDatabaseAccessor(redisConnection: RedisConnection, db: Int)
    : AbstractRedisOrderBookDatabaseAccessor(redisConnection, db, KEY_PREFIX_ORDER, "stop"), StopOrderBookDatabaseAccessor {

    override fun loadStopLimitOrders() = loadOrders()

    override fun updateStopOrderBook(assetPairId: String, isBuy: Boolean, orderBook: Collection<LimitOrder>) {
        // Nothing to do
    }

    companion object {
        private const val KEY_PREFIX_ORDER = "StopLimitOrder:"
    }
}