package com.swisschain.matching.engine.outgoing.senders.impl

import com.swisschain.matching.engine.daos.OutgoingEventData
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSender
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSendersHolder

class SpecializedEventSendersHolderImpl(specializedEventSenders: List<SpecializedEventSender<*>>) : SpecializedEventSendersHolder {

    private val sendersByHandledClass = specializedEventSenders.groupBy { it.getEventClass() }

    override fun <T : OutgoingEventData> getSenders(event: T): List<SpecializedEventSender<T>> {
        val senders = sendersByHandledClass[event::class.java]
                ?: return emptyList()
        return senders as List<SpecializedEventSender<T>>
    }
}