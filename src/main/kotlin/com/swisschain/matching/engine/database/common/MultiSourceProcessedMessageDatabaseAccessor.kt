package com.swisschain.matching.engine.database.common

import com.swisschain.matching.engine.database.ReadOnlyProcessedMessagesDatabaseAccessor
import com.swisschain.matching.engine.database.redis.accessor.impl.RedisProcessedMessagesDatabaseAccessor
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.utils.logging.ThrottlingLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.HashSet
import java.util.Optional

@Component("MultiSourceProcessedMessageDatabaseAccessor")
class MultiSourceProcessedMessageDatabaseAccessor
@Autowired constructor(private val redisProcessedMessagesDatabaseAccessor: Optional<RedisProcessedMessagesDatabaseAccessor>): ReadOnlyProcessedMessagesDatabaseAccessor {
    companion object {
        val LOGGER = ThrottlingLogger.getLogger(MultiSourceProcessedMessageDatabaseAccessor::class.java.name)
    }

    override fun get(): Set<ProcessedMessage> {
        val result = HashSet<ProcessedMessage>()
        redisProcessedMessagesDatabaseAccessor.ifPresent({ result.addAll(it.get()) })

        LOGGER.info("Loaded ${result.size} processed messages from multisource datasource")
        return result
    }
}