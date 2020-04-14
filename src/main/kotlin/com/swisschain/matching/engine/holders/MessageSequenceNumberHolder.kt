package com.swisschain.matching.engine.holders

import com.swisschain.matching.engine.database.ReadOnlyMessageSequenceNumberDatabaseAccessor
import org.springframework.stereotype.Component

@Component
class MessageSequenceNumberHolder(messageSequenceNumberDatabaseAccessor: ReadOnlyMessageSequenceNumberDatabaseAccessor) {

    private var sequenceNumber = messageSequenceNumberDatabaseAccessor.getSequenceNumber()
    private var persistedSequenceNumber = sequenceNumber

    @Synchronized
    fun getNewValue() = ++sequenceNumber

    @Synchronized
    fun getValueToPersist(): Long? {
        return if (persistedSequenceNumber != sequenceNumber) {
            persistedSequenceNumber = sequenceNumber
            sequenceNumber
        } else {
            null
        }
    }

}