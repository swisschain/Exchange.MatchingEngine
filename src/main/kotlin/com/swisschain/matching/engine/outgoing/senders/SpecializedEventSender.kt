package com.swisschain.matching.engine.outgoing.senders

import com.swisschain.matching.engine.daos.OutgoingEventData

interface SpecializedEventSender<T : OutgoingEventData> {
    fun getEventClass(): Class<T>
    fun sendEvent(event: T)
}