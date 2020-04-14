package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header

abstract class Event(val header: Header) {

    fun sequenceNumber() = header.sequenceNumber

    abstract fun buildGeneratedMessage(): OutgoingMessages.MessageWrapper
}