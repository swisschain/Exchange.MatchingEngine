package com.swisschain.matching.engine.database.common

import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import java.util.Optional

@FunctionalInterface
interface PersistenceManagerFactory {
    fun get(redisConnection: Optional<RedisConnection>): PersistenceManager
}