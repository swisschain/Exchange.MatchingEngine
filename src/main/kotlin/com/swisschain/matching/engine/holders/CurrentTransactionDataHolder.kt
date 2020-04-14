package com.swisschain.matching.engine.holders
import com.swisschain.matching.engine.messages.MessageType
import org.springframework.stereotype.Component

@Component
class CurrentTransactionDataHolder {
    private val messageTypeThreadLocal = ThreadLocal<MessageType>()

    fun getMessageType(): MessageType? {
        return messageTypeThreadLocal.get()
    }

    fun setMessageType(messageType: MessageType) {
        messageTypeThreadLocal.set(messageType)
    }
}