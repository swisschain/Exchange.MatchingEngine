package com.swisschain.matching.engine.outgoing.senders

import com.swisschain.matching.engine.daos.OutgoingEventData

interface SpecializedEventSendersHolder {
    fun <T: OutgoingEventData> getSenders(event: T): List<SpecializedEventSender<T>>
}