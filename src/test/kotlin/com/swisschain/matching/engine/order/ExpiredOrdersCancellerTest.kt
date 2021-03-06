package com.swisschain.matching.engine.order

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.context.LimitOrderCancelOperationContext
import com.swisschain.matching.engine.daos.order.OrderTimeInForce
import com.swisschain.matching.engine.incoming.MessageRouter
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import java.util.Date
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExpiredOrdersCancellerTest : AbstractTest() {

    @Autowired
    private lateinit var messagesRouter: MessageRouter

    @Autowired
    private lateinit var expiryOrdersQueue: ExpiryOrdersQueue

    private val orders = mutableListOf<LimitOrder>()

    @Before
    fun setUp() {
        val now = Date()
        orders.addAll(listOf(buildLimitOrder(timeInForce = OrderTimeInForce.GTD, expiryTime = date(now, 500), uid = "1"),
                buildLimitOrder(timeInForce = OrderTimeInForce.GTD, expiryTime = date(now, 1500), uid = "2")))
        orders.forEach { expiryOrdersQueue.addIfOrderHasExpiryTime(it) }
    }

    @Test
    fun testCancelExpiredOrders() {
        val queue = messagesRouter.preProcessedMessageQueue
        val service = ExpiredOrdersCanceller(expiryOrdersQueue, messagesRouter)

        service.cancelExpiredOrders()
        assertEquals(0, queue.size)

        Thread.sleep(800)
        service.cancelExpiredOrders()
        assertEquals(1, queue.size)
        var messageContext = queue.poll().context as LimitOrderCancelOperationContext
        assertEquals(1, messageContext.limitOrderIds.size)
        assertEquals("1", messageContext.limitOrderIds.single())

        queue.clear()
        expiryOrdersQueue.removeIfOrderHasExpiryTime(orders[0])

        Thread.sleep(800)
        service.cancelExpiredOrders()
        assertEquals(1, queue.size)
        messageContext = queue.poll().context as LimitOrderCancelOperationContext
        assertEquals(1, messageContext.limitOrderIds.size)
        assertEquals("2", messageContext.limitOrderIds.single())
    }

    private fun date(date: Date, delta: Long) = Date(date.time + delta)
}