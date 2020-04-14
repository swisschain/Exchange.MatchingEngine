package com.swisschain.matching.engine.deduplication

interface ProcessedMessagesCache {
    fun addMessage(message: ProcessedMessage)

    fun isProcessed(type: Byte, messageId: String): Boolean
}