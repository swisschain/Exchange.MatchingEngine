package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (LimitOrderCancelServiceTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LimitOrderCancelServiceTest : AbstractTest() {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))

            return testDictionariesDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Before
    fun setUp() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "5", price = 100.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "3", price = 300.0, volume = -1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "6", price = 200.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "7", price = 300.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "8", price = 400.0))

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURCHF", "EUR", "CHF", 5))

        testBalanceHolderWrapper.updateBalance("Client1", "EUR", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance("Client1", "EUR",  1.0)
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 1000.0)
        initServices()
    }

    @Test
    fun testCancel() {
        limitOrderCancelService.processMessage(messageBuilder.buildLimitOrderCancelWrapper("3"))

        assertEquals(1, outgoingOrderBookQueue.size)

        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "EUR"))

        val order = testOrderDatabaseAccessor.loadLimitOrders().find { it.id == "3" }
        assertNull(order)
        assertEquals(4, testOrderDatabaseAccessor.loadLimitOrders().size)

        val previousOrders = genericLimitOrderService.searchOrders(DEFAULT_BROKER, "Client1", "EURUSD", true)
        assertEquals(4, previousOrders.size)
        assertFalse(previousOrders.any { it.externalId == "3" })

        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, executionEvent.balanceUpdates?.size)
        assertEventBalanceUpdate("Client1", "EUR", "1000", "1000", "1", "0", executionEvent.balanceUpdates!!)
        assertEquals(1, executionEvent.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, executionEvent.orders.first().status)
    }

    @Test
    fun testMultiCancel() {
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5))
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 1.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "10", walletId = "Client2", assetId = "BTCUSD", price = 9000.0, volume = -0.5)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "11", walletId = "Client2", assetId = "BTCUSD", price = 9100.0, volume = -0.3)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "12", walletId = "Client2", assetId = "BTCUSD", price = 9200.0, volume = -0.2)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "13", walletId = "Client2", assetId = "BTCUSD", price = 8000.0, volume = 0.1)))
        clearMessageQueues()

        limitOrderCancelService.processMessage(messageBuilder.buildLimitOrderCancelWrapper(listOf("10", "11", "13")))
        assertOrderBookSize("BTCUSD", false, 1)
        assertOrderBookSize("BTCUSD", true, 0)

        assertBalance("Client2", "BTC", 1.0, 0.2)
        assertBalance("Client2", "USD", 1000.0, 0.0)

        assertEquals(2, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, executionEvent.orders.size)
        executionEvent.orders.forEach {
            assertEquals(OutgoingOrderStatus.CANCELLED, it.status)
        }

        assertEquals(2, executionEvent.balanceUpdates?.size)

        assertEventBalanceUpdate("Client2", "BTC", "1", "1", "1", "0.2", executionEvent.balanceUpdates!!)
        assertEventBalanceUpdate("Client2", "USD", "1000", "1000", "800", "0", executionEvent.balanceUpdates!!)

        assertEquals(3, executionEvent.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, executionEvent.orders[0].status)
        assertEquals(OutgoingOrderStatus.CANCELLED, executionEvent.orders[1].status)
        assertEquals(OutgoingOrderStatus.CANCELLED, executionEvent.orders[2].status)
    }
}