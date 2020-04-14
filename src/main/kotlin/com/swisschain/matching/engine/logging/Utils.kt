package com.swisschain.matching.engine.logging

import com.swisschain.matching.engine.daos.Message
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event

fun toLogMessage(message: Any): Message {
        return when (message) {
            is Event -> {
                val header = message.header
                Message(header.sequenceNumber, header.messageId, header.requestId, header.eventType, header.timestamp, message.buildGeneratedMessage().toString())
            }
            else -> {
                throw IllegalArgumentException("Unknown message type: ${message::class.java.name}")
            }
        }
    }
