package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.Header
import com.swisschain.matching.engine.outgoing.messages.v2.events.common.ReservedBalanceUpdate
import org.junit.Test
import java.util.Date
import kotlin.test.assertEquals


class ReservedBalanceUpdateEventTest {

    @Test
    fun buildGeneratedMessage() {
        val header = Header(MessageType.RESERVED_BALANCE_UPDATE, 1L, "messageUID", "requestUID", "version", Date(), "EVENT_TYPE")
        val balanceUpdates = listOf(BalanceUpdate("Wallet1", "Asset1", "1", "2", "3", "4"),
                BalanceUpdate("Wallet2", "Asset2", "21", "22", "23", "24"))
        val reservedBalanceUpdate = ReservedBalanceUpdate("Wallet3", "Asset3", "-7")
        val serializedEvent = ReservedBalanceUpdateEvent(header, balanceUpdates, reservedBalanceUpdate).buildGeneratedMessage()

        val event = serializedEvent.message.unpack(OutgoingMessages.ReservedBalanceUpdateEvent::class.java)
        assertEquals(event.header.messageId, "messageUID")
        assertEquals(event.header.requestId, "requestUID")
        assertEquals(event.header.eventType, "EVENT_TYPE")
        assertEquals(event.balanceUpdatesCount, 2)
        assertBalanceUpdate("Wallet1", "Asset1", "1", "2", "3", "4", event.balanceUpdatesList)
        assertBalanceUpdate("Wallet2", "Asset2", "21", "22", "23", "24", event.balanceUpdatesList)
        assertEquals("Wallet3", event.reservedBalanceUpdate.walletId)
        assertEquals("Asset3", event.reservedBalanceUpdate.assetId)
        assertEquals("-7", event.reservedBalanceUpdate.volume)
    }

}