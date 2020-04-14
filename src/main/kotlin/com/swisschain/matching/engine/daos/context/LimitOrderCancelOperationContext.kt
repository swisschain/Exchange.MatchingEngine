package com.swisschain.matching.engine.daos.context

import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.messages.MessageType

data class LimitOrderCancelOperationContext(
        val brokerId: String,
        val uid: String,
        val messageId: String,
        val processedMessage: ProcessedMessage,
        val limitOrderIds: Set<String>,
        val messageType: MessageType)