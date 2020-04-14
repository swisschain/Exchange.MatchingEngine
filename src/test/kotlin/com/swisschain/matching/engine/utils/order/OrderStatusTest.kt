package com.swisschain.matching.engine.utils.order

import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.order.OrderStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderStatusTest {

    @Test
    fun testOrderStatusConvertToMessageStatus() {
        val messageStatus = MessageStatusUtils.toMessageStatus(OrderStatus.ReservedVolumeGreaterThanBalance)

        assertEquals(MessageStatus.RESERVED_VOLUME_HIGHER_THAN_BALANCE, messageStatus)
    }
}