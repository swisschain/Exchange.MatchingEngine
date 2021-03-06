package com.swisschain.matching.engine.order

import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.common.entity.PersistenceData
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.order.transaction.ExecutionContext
import org.springframework.stereotype.Component

@Component
class ExecutionPersistenceService(private val persistenceManager: PersistenceManager) {

    fun persist(messageWrapper: MessageWrapper?,
                executionContext: ExecutionContext,
                sequenceNumber: Long? = null): Boolean {

        if (messageWrapper?.triedToPersist == true) {
            executionContext.error("There already was attempt to persist data")
            return messageWrapper.persisted
        }

        val persisted = persistenceManager.persist(PersistenceData(executionContext.walletOperationsProcessor.persistenceData(),
                executionContext.processedMessage,
                executionContext.orderBooksHolder.getPersistenceData(),
                executionContext.stopOrderBooksHolder.getPersistenceData(),
                sequenceNumber))
        messageWrapper?.triedToPersist = true
        messageWrapper?.persisted = persisted
        if (persisted) {
            executionContext.apply()
        } else {
            executionContext.error("Unable to persist result")
        }
        return persisted
    }
}