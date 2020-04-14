package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header
import java.util.Date
import com.swisschain.matching.engine.messages.MessageType as IncomingMessageType

abstract class EventBuilder<in TEventData : EventData, out TResult : Event> {

    companion object {
        private const val VERSION = "1"
    }

    private var sequenceNumber: Long? = null
    private var messageId: String? = null
    private var requestId: String? = null
    private var date: Date? = null
    private var incomingMessageType: IncomingMessageType? = null

    protected abstract fun getMessageType(): MessageType

    fun setHeaderData(sequenceNumber: Long,
                      messageId: String,
                      requestId: String,
                      date: Date,
                      incomingMessageType: IncomingMessageType): EventBuilder<TEventData, TResult> {
        this.sequenceNumber = sequenceNumber
        this.messageId = messageId
        this.requestId = requestId
        this.date = date
        this.incomingMessageType = incomingMessageType
        return this
    }

    abstract fun setEventData(eventData: TEventData): EventBuilder<TEventData, TResult>

    protected abstract fun buildEvent(header: Header): TResult

    fun build(): TResult {
        val header = Header(getMessageType(),
                sequenceNumber!!,
                messageId!!,
                requestId!!,
                VERSION,
                date!!,
                incomingMessageType!!.name)
        return buildEvent(header)
    }

}