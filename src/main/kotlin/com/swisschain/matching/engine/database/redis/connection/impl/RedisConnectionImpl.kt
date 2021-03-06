package com.swisschain.matching.engine.database.redis.connection.impl

import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import com.swisschain.matching.engine.database.redis.events.RedisFailureEvent
import com.swisschain.matching.engine.utils.config.RedisConfig
import com.swisschain.utils.logging.ThrottlingLogger
import org.springframework.context.ApplicationEventPublisher
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction
import redis.clients.jedis.exceptions.JedisException

class RedisConnectionImpl(private val connectionName: String,
                          private val redisConfig: RedisConfig,
                          private val applicationEventPublisher: ApplicationEventPublisher): RedisConnection {
    private var jedis: Jedis

    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(RedisConnectionImpl::class.java.name)
    }
    init {
        jedis = getRedisConnection()
    }

    @Synchronized
    override fun transactionalResource(unitOfWork: (transaction: Transaction) -> Unit) {
        return resource {jedis -> jedis.multi().use(unitOfWork)}
    }

    @Synchronized
    override fun resource(unitOfWork: (Jedis) -> Unit) {
        return try {
            invokeUnitOfWork(unitOfWork)
        } catch (e: JedisException) {
            LOGGER.error("Redis exception occurred, $connectionName", e)
            applicationEventPublisher.publishEvent(RedisFailureEvent(connectionName))
            throw e
        }
    }

    private fun invokeUnitOfWork(unitOfWork: (Jedis) -> Unit) {
        return try {
            unitOfWork.invoke(jedis)
        } catch (e: JedisException) {
            LOGGER.error("Redis exception occurred, $connectionName; trying to reconnect and retry.", e)
            reconnect()
            unitOfWork.invoke(jedis)
        }
    }

    @Synchronized
    override fun reconnect() {
        tryClose()
        jedis = getRedisConnection()
    }

    private fun tryClose() {
        try {
            if (jedis.isConnected) {
                jedis.close()
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to close jedis connection", e)
        }
    }

    private fun getRedisConnection(): Jedis {
        val jedis = Jedis(redisConfig.host, redisConfig.port, redisConfig.timeout, redisConfig.useSsl)
        jedis.connect()
        if (redisConfig.password != null) {
            jedis.auth(redisConfig.password)
        }

        return jedis
    }
}