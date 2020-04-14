package com.swisschain.matching.engine.database.redis.connection.impl

import com.swisschain.matching.engine.database.redis.connection.RedisConnection
import com.swisschain.matching.engine.database.redis.connection.RedisConnectionFactory
import com.swisschain.matching.engine.utils.config.Config
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class RedisConnectionFactoryImpl (private val applicationEventPublisher: ApplicationEventPublisher,
                                  private val config: Config): RedisConnectionFactory {
    override fun getConnection(name: String): RedisConnection? {
        return RedisConnectionImpl(name, config.me.redis, applicationEventPublisher)
    }
}