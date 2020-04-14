package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.order.OrderTimeInForce
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.order.ExpiryOrdersQueue
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderType
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrderWrapper
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMultiLimitOrderWrapper
import com.swisschain.matching.engine.utils.assertEquals
import com.swisschain.matching.engine.utils.getSetting
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType as OutgoingMessageType
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (StopLimitOrderTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StopLimitOrderTest : AbstractTest() {

    @TestConfiguration
    class Config {

        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()

            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))

            return testDictionariesDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var testSettingsDatabaseAccessor: TestSettingsDatabaseAccessor

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Autowired
    private lateinit var expiryOrdersQueue: ExpiryOrdersQueue

    @Before
    fun setUp() {
        testBalanceHolderWrapper.updateBalance("Client1", "BTC", 1.0)
        testBalanceHolderWrapper.updateReservedBalance("Client1", "BTC", 0.0)
        testBalanceHolderWrapper.updateBalance("Client1", "USD", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance("Client1", "USD", 0.0)

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 6))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "USD", 6, maxValue = BigDecimal.valueOf(8000)))
        initServices()
    }

    @Test
    fun testNotEnoughFunds() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = -1.01,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9500.0, lowerPrice = 9000.0
        )))

        assertEquals(0, genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).size)
        assertEquals(0, stopOrderDatabaseAccessor.getStopOrders("BTCUSD", false).size)

        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(executionEvent.header.eventType, MessageType.LIMIT_ORDER.name)
        assertEquals(executionEvent.header.messageType, OutgoingMessageType.ORDER)
        assertEquals(1, executionEvent.orders.size)
        assertEquals(OutgoingOrderStatus.REJECTED, executionEvent.orders.first().status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, executionEvent.orders.first().rejectReason)
        assertEquals(0, executionEvent.balanceUpdates!!.size)
    }

    @Test
    fun testAddStopLimitOrder() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = -0.01,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9500.0, lowerPrice = 9000.0
        )))

        assertEquals(1, stopOrderDatabaseAccessor.getStopOrders("BTCUSD", false).size)
        assertEquals(1, genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).size)
        assertEquals(BigDecimal.valueOf(0.01), balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(BigDecimal.valueOf(0.01), testWalletDatabaseAccessor.getReservedBalance("Client1", "BTC"))

        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(executionEvent.header.eventType, MessageType.LIMIT_ORDER.name)
        assertEquals(executionEvent.header.messageType, OutgoingMessageType.ORDER)
        assertEquals(1, executionEvent.orders.size)
        assertEquals(OutgoingOrderStatus.PENDING, executionEvent.orders.first().status)
        assertNull(executionEvent.orders.first().rejectReason)
        assertEquals(1, executionEvent.balanceUpdates!!.size)
        assertEventBalanceUpdate("Client1", "BTC", "1", "1", "0", "0.01", executionEvent.balanceUpdates!!)
    }

    @Test
    fun testAddStopLimitOrderAndCancelAllPrevious() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = -0.01,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9500.0, lowerPrice = 9000.0
        )))
        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", type = LimitOrderType.STOP_LIMIT, volume = -0.02,
                lowerLimitPrice = 9500.0, lowerPrice = 9000.0, upperLimitPrice = 10500.0, upperPrice = 10000.0

        ), true))

        assertEquals(1, stopOrderDatabaseAccessor.getStopOrders("BTCUSD", false).size)
        assertEquals(1, genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).size)
        assertEquals(BigDecimal.valueOf(-0.02), genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).first().volume)
        assertEquals(BigDecimal.valueOf(0.02), balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(BigDecimal.valueOf(0.02), testWalletDatabaseAccessor.getReservedBalance("Client1", "BTC"))

        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(executionEvent.header.eventType, MessageType.LIMIT_ORDER.name)
        assertEquals(executionEvent.header.messageType, OutgoingMessageType.ORDER)
        assertEquals(2, executionEvent.orders.size)
        assertEquals(1, executionEvent.orders.filter { it.status == OutgoingOrderStatus.PENDING }.size)
        assertEquals(1, executionEvent.orders.filter { it.status == OutgoingOrderStatus.CANCELLED }.size)
        assertEquals(1, executionEvent.balanceUpdates!!.size)
        assertEventBalanceUpdate("Client1", "BTC", "1", "1", "0.01", "0.02", executionEvent.balanceUpdates!!)
    }

    @Test
    fun testCancelStopLimitOrder() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = -0.01,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9500.0, lowerPrice = 9000.0
        )))

        clearMessageQueues()
        limitOrderCancelService.processMessage(messageBuilder.buildLimitOrderCancelWrapper("order1"))

        assertTrue(stopOrderDatabaseAccessor.getStopOrders("BTCUSD", false).isEmpty())
        assertTrue(genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).isEmpty())
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance("Client1", "BTC"))

        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(executionEvent.header.eventType, MessageType.LIMIT_ORDER_CANCEL.name)
        assertEquals(executionEvent.header.messageType, OutgoingMessageType.ORDER)
        assertEquals(1, executionEvent.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, executionEvent.orders.first().status)
        assertNull(executionEvent.orders.first().rejectReason)
        assertEquals(1, executionEvent.balanceUpdates!!.size)
        assertEventBalanceUpdate("Client1", "BTC", "1", "1", "0.01", "0", executionEvent.balanceUpdates!!)
    }

    @Test
    fun testProcessStopLimitOrder() {
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 1000.0)
        testBalanceHolderWrapper.updateBalance("Client3", "BTC", 1.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = -0.01,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9500.0, lowerPrice = 9000.0
        )))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order2", walletId = "Client1", assetId = "BTCUSD", volume = -0.03,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9500.5, lowerPrice = 9000.0
        )))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = 0.03, price = 9501.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = 0.03, price = 9499.0)))

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client3", assetId = "BTCUSD", volume = -0.03, price = 9501.0)))

        assertBalance("Client1", "BTC", reserved = 0.01)
        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.last() as ExecutionEvent
        assertEquals(executionEvent.header.eventType, MessageType.LIMIT_ORDER.name)
        assertEquals(executionEvent.header.messageType, OutgoingMessageType.ORDER)
        assertEquals(5, executionEvent.orders.size)
        val eventStopOrder = executionEvent.orders.single { it.externalId == "order2" }
        val childLimitOrder = executionEvent.orders.single { it.parentExternalId == "order2" }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(childLimitOrder.externalId, eventStopOrder.childExternalId)
        assertNull(eventStopOrder.parentExternalId)
        assertEquals(OutgoingOrderStatus.MATCHED, childLimitOrder.status)
        assertNull(childLimitOrder.childExternalId)

        assertEquals(6, executionEvent.balanceUpdates!!.size)
        assertEventBalanceUpdate("Client1", "BTC", "1", "0.97", "0.04", "0.01", executionEvent.balanceUpdates!!)
        assertEventBalanceUpdate("Client2", "USD", "1000", "430", "570", "0", executionEvent.balanceUpdates!!)
    }

    @Test
    fun testProcessStopOrderAfterRejectLimitOrderWithCancelPrevious() {
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.1)
        testBalanceHolderWrapper.updateBalance("Client3", "BTC", 0.1)

        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.1, price = 10000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client3", assetId = "BTCUSD", volume = -0.1, price = 11000.0)))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = 0.05, type = LimitOrderType.STOP_LIMIT,
                upperLimitPrice = 10500.0, upperPrice = 11000.0
        )))

        clearMessageQueues()
        // cancel previous orders and will be rejected due to not enough funds
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.2, price = 10000.0), true))

        assertStopOrderBookSize("BTCUSD", false, 0)
        assertBalance("Client1", "USD", reserved = 0.0)

        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.last() as ExecutionEvent
        assertEquals(executionEvent.header.eventType, MessageType.LIMIT_ORDER.name)
        assertEquals(executionEvent.header.messageType, OutgoingMessageType.ORDER)
        assertEquals(5, executionEvent.orders.size)
        val eventClientOrder = executionEvent.orders.single { it.walletId == "Client1" && it.orderType == OrderType.LIMIT }
        assertEquals(1, eventClientOrder.trades?.size)
        assertEquals("BTC", eventClientOrder.trades!!.first().baseAssetId)
        assertEquals("0.05", eventClientOrder.trades!!.first().baseVolume)
        assertEquals("USD", eventClientOrder.trades!!.first().quotingAssetId)
        assertEquals("-550", eventClientOrder.trades!!.first().quotingVolume)
    }

    @Test
    fun testProcessStopLimitOrderAfterLimitOrderCancellation() {
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.3)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = 0.09,
                type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 10000.0, upperPrice = 10500.0
        )))
        assertStopOrderBookSize("BTCUSD", true, 1)
        assertBalance("Client1", "USD", reserved = 945.0)

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", uid = "order2", assetId = "BTCUSD", volume = -0.1, price = 9000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.2, price = 10000.0)))

        clearMessageQueues()
        limitOrderCancelService.processMessage(messageBuilder.buildLimitOrderCancelWrapper("order2"))

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertBalance("Client1", "USD", 100.0, 0.0)

        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.last() as ExecutionEvent
        assertEquals(4, executionEvent.orders.size)

        val eventStopOrder = executionEvent.orders.single { it.externalId == "order1" }
        val childLimitOrder = executionEvent.orders.single { it.parentExternalId == "order1" }
        assertEquals(OrderType.STOP_LIMIT, eventStopOrder.orderType)
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(childLimitOrder.externalId, eventStopOrder.childExternalId)
        assertEquals("10500", eventStopOrder.price)

        assertEquals(OrderType.LIMIT, childLimitOrder.orderType)
        assertEquals(OutgoingOrderStatus.MATCHED, childLimitOrder.status)
        assertEquals("10500", childLimitOrder.price)
    }

    @Test
    fun testRejectStopOrderDuringMatching() {
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 1000.0)
        testBalanceHolderWrapper.updateBalance("Client3", "BTC", 1.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = 0.03, price = 9600.0)))

        // Order leading to negative spread. Added to reject stop order.
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = 0.03, price = 9500.0)))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = -0.01,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9500.5, lowerPrice = 9000.0
        )))

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client3", assetId = "BTCUSD", volume = -0.03, price = 9600.0)))

        assertOrderBookSize("BTCUSD", true, 1)
        assertStopOrderBookSize("BTCUSD", false, 0)
        assertBalance("Client1", "BTC", reserved = 0.0)

        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.single() as ExecutionEvent
        assertEquals(4, executionEvent.orders.size)
        val eventStopOrder = executionEvent.orders.single { it.walletId == "Client1" && it.orderType == OrderType.STOP_LIMIT }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        val childLimitOrder = executionEvent.orders.single { it.externalId == eventStopOrder.childExternalId }
        assertEquals(OutgoingOrderStatus.REJECTED, childLimitOrder.status)
        assertEquals(OrderRejectReason.LEAD_TO_NEGATIVE_SPREAD, childLimitOrder.rejectReason)
    }

    @Test
    fun testProcessStopLimitOrderAfterMarketOrder() {
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.3)
        testBalanceHolderWrapper.updateBalance("Client3", "USD", 1900.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = 0.09,
                type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 10000.0, upperPrice = 10500.0
        )))
        assertStopOrderBookSize("BTCUSD", true, 1)
        assertBalance("Client1", "USD", reserved = 945.0)

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.1, price = 9000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.2, price = 10000.0, uid = "TwoTradesOrder")))

        clearMessageQueues()
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = "Client3", assetId = "BTCUSD", volume = 0.2)))

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 1)
        assertBalance("Client1", "USD", 100.0, 0.0)

        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.last() as ExecutionEvent
        assertEquals(5, executionEvent.orders.size)
        assertEquals(1, executionEvent.orders.filter { it.externalId == "order1" }.size)
        assertEquals(2, executionEvent.orders.single { it.externalId == "TwoTradesOrder" }.trades?.size)

        val eventStopOrder = executionEvent.orders.single { it.externalId == "order1" }
        val childLimitOrder = executionEvent.orders.single { it.parentExternalId == "order1" }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals("10500", eventStopOrder.price)
        assertEquals(OutgoingOrderStatus.MATCHED, childLimitOrder.status)
        assertEquals("10500", childLimitOrder.price)
    }

    private fun processStopLimitOrderAfterMultiLimitOrder(forTrustedClient: Boolean) {
        if (forTrustedClient) {
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("Client3"))
        }

        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.3)
        testBalanceHolderWrapper.updateBalance("Client3", "BTC", 1.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = 0.09,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 8500.0, lowerPrice = 9000.0, upperLimitPrice = 10000.0, upperPrice = 10500.0
        )))
        assertStopOrderBookSize("BTCUSD", true, 1)
        assertBalance("Client1", "USD", reserved = 945.0)

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.1, price = 9000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.2, price = 10000.0)))

        clearMessageQueues()
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD",
                "Client3",
                listOf(IncomingLimitOrder(-0.1, 11000.0),
                        IncomingLimitOrder(-0.05, 8500.0))))
    }

    @Test
    fun testProcessStopLimitOrderAfterTrustedClientMultiLimitOrder() {
        processStopLimitOrderAfterMultiLimitOrder(true)

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertBalance("Client1", "USD", 215.0, 0.0)

        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.last() as ExecutionEvent
        assertEquals(4, executionEvent.orders.size)

        val eventStopOrder = executionEvent.orders.single { it.externalId == "order1" }
        val childLimitOrder = executionEvent.orders.single { it.parentExternalId == "order1" }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(childLimitOrder.externalId, eventStopOrder.childExternalId)
        assertNull(eventStopOrder.parentExternalId)
        assertEquals("9000", eventStopOrder.price)

        assertEquals(OutgoingOrderStatus.MATCHED, childLimitOrder.status)
        assertNull(childLimitOrder.childExternalId)
        assertEquals("9000", childLimitOrder.price)
    }

    @Test
    fun testProcessStopLimitOrderAfterClientMultiLimitOrder() {
        processStopLimitOrderAfterMultiLimitOrder(false)

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertBalance("Client1", "USD", 215.0, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.single() as ExecutionEvent
        assertEquals(5, executionEvent.orders.size)
        assertEquals(1, executionEvent.orders.filter { it.externalId == "order1" }.size)

        val eventStopOrder = executionEvent.orders.single { it.externalId == "order1" }
        val childLimitOrder = executionEvent.orders.single { it.parentExternalId == "order1" }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(0, eventStopOrder.trades?.size)
        assertEquals(childLimitOrder.externalId, eventStopOrder.childExternalId)
        assertNull(eventStopOrder.parentExternalId)
        assertEquals("9000", eventStopOrder.price)

        assertEquals(OutgoingOrderStatus.MATCHED, childLimitOrder.status)
        assertEquals(2, childLimitOrder.trades?.size)
        assertNull(childLimitOrder.childExternalId)
        assertEquals("9000", childLimitOrder.price)
    }

    private fun processStopLimitOrderAfterMultiLimitOrderCancellation(forTrustedClient: Boolean) {
        if (forTrustedClient) {
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("Client2"))
        }

        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.3)
        testBalanceHolderWrapper.updateBalance("Client3", "BTC", 0.1)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = 0.09,
                type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 10000.0, upperPrice = 10500.0
        )))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD",
                "Client2",
                listOf(IncomingLimitOrder(-0.1, 9000.0),
                        IncomingLimitOrder(-0.2, 10000.0))))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client3", assetId = "BTCUSD", volume = -0.1, price = 10000.0)))

        clearMessageQueues()
        val message = messageBuilder.buildLimitOrderMassCancelWrapper("Client2", "BTCUSD", false)
        limitOrderMassCancelService.processMessage(message)
    }

    @Test
    fun `process stop limit order after trusted client multi limit orders cancellation`() {
        processStopLimitOrderAfterMultiLimitOrderCancellation(true)

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertBalance("Client1", "USD", 100.0, 0.0)

        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, executionEvent.orders.size)
        assertNotNull(executionEvent.orders.singleOrNull { it.externalId == "order1" })
        assertNotNull(executionEvent.orders.singleOrNull { it.parentExternalId == "order1" })

        val eventStopOrder = executionEvent.orders.single { it.externalId == "order1" }
        val childLimitOrder = executionEvent.orders.single { it.parentExternalId == "order1" }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(childLimitOrder.externalId, eventStopOrder.childExternalId)
        assertNull(eventStopOrder.parentExternalId)
        assertEquals("10500", eventStopOrder.price)

        assertEquals(OutgoingOrderStatus.MATCHED, childLimitOrder.status)
        assertNull(childLimitOrder.childExternalId)
        assertEquals("10500", childLimitOrder.price)
    }

    @Test
    fun `process stop limit order after client multi limit orders cancellation`() {
        processStopLimitOrderAfterMultiLimitOrderCancellation(false)

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertBalance("Client1", "USD", 100.0, 0.0)

        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.last() as ExecutionEvent
        assertEquals(5, executionEvent.orders.size)
        assertNotNull(executionEvent.orders.singleOrNull { it.externalId == "order1" })
        assertNotNull(executionEvent.orders.singleOrNull { it.parentExternalId == "order1" })

        val eventStopOrder = executionEvent.orders.single { it.externalId == "order1" }
        val childLimitOrder = executionEvent.orders.single { it.parentExternalId == "order1" }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(childLimitOrder.externalId, eventStopOrder.childExternalId)
        assertNull(eventStopOrder.parentExternalId)
        assertEquals("10500", eventStopOrder.price)

        assertEquals(OutgoingOrderStatus.MATCHED, childLimitOrder.status)
        assertNull(childLimitOrder.childExternalId)
        assertEquals("10500", childLimitOrder.price)
    }

    @Test
    fun testProcessStopLimitOrderAfterMinVolumeOrdersCancellation() {
        testBalanceHolderWrapper.updateBalance("Client1", "EUR", 5.0)
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 10000.0)

        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2))
        initServices()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", "Client2", listOf(
                IncomingLimitOrder(-0.00009, 10000.0),
                IncomingLimitOrder(-0.09, 11000.0))))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("EURUSD", "Client2", listOf(
                IncomingLimitOrder(1.0, 1.1),
                IncomingLimitOrder(6.0, 1.0))))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = 0.09,
                type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 11000.0, upperPrice = 11000.0
        )))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "EURUSD", volume = -5.0,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 1.0, lowerPrice = 0.99
        )))

        assertStopOrderBookSize("BTCUSD", true, 1)
        assertOrderBookSize("BTCUSD", false, 2)
        assertStopOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 2)

        assertBalance("Client1", "USD", reserved = 990.0)
        assertBalance("Client1", "EUR", reserved = 5.0)

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5, BigDecimal.valueOf(0.0001)))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2, BigDecimal.valueOf(5.0)))

        initServices()

        clearMessageQueues()
        minVolumeOrderCanceller.cancel()

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertStopOrderBookSize("EURUSD", false, 0)
        assertOrderBookSize("EURUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 0)

        assertBalance("Client1", "USD", 15.0, 0.0)
        assertBalance("Client1", "EUR", 0.0, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(8, event.orders.size)
        assertEquals(6, event.balanceUpdates?.size)
        assertEquals(3, event.orders.filter { it.status == OutgoingOrderStatus.CANCELLED }.size)
        assertEquals(1, event.orders.filter { it.status == OutgoingOrderStatus.CANCELLED && it.trades?.isNotEmpty() == true }.size)
    }

    @Test
    fun testProcessStopLimitOrdersChain() {
        testBalanceHolderWrapper.updateBalance("Client3", "USD", 10000.0)
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 10000.0)
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 1.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = 0.1, price = 10000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = 0.1, price = 9500.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = 0.1, price = 9000.0)))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = -0.2,
                type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 10500.0, upperPrice = 10000.0
        )))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = -0.1,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9500.0, lowerPrice = 9500.0
        )))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = -0.2,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9000.0, lowerPrice = 9000.0
        )))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client3", assetId = "BTCUSD", volume = 0.1,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9000.0, lowerPrice = 9000.0
        )))

        assertEquals(3, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(true).size)
        assertEquals(1, genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(true).size)
        assertEquals(3, genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).size)
        assertEquals(BigDecimal.valueOf(0.5), balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(BigDecimal.valueOf( 900.0), balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client3", "USD"))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = 0.1, price = 10500.0)))


        assertTrue(genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(true).isEmpty())
        assertTrue(genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).isEmpty())
        assertTrue(genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(true).isEmpty())
        assertTrue(genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).isEmpty())
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client3", "USD"))
    }

    @Test
    fun testProcessBothSideStopLimitOrders() {
        testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("Client2"))

        testBalanceHolderWrapper.updateBalance("Client3", "USD", 1050.0)
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.1)
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 900.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client1", assetId = "BTCUSD", volume = -0.1,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9000.0, lowerPrice = 8500.0
        )))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = "Client3", assetId = "BTCUSD", volume = 0.1,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 10000.0, lowerPrice = 10500.0
        )))

        assertEquals(1, genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(true).size)
        assertEquals(1, genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).size)
        assertEquals(BigDecimal.valueOf( 0.1), balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(BigDecimal.valueOf(1050.0), balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client3", "USD"))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD",
                "Client2",
                listOf(IncomingLimitOrder(-0.1, 10000.0),
                        IncomingLimitOrder(0.1, 9000.0))))

        assertTrue(genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(true).isEmpty())
        assertTrue(genericStopLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).isEmpty())
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client3", "USD"))
    }

    @Test
    fun testProcessStopLimitOrderImmediately() {
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.1)
        initServices()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.1, price = 9900.0)))
        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = 0.1,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 9900.0, lowerPrice = 10000.0)))

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 0)

        assertBalance("Client1", "USD", 10.0, 0.0)

        // new contract event assertion
        assertEquals(1, clientsEventsQueue.size)
        val executionEvent = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, executionEvent.orders.size)
        assertNotNull(executionEvent.orders.singleOrNull { it.externalId == "order1" })
        assertNotNull(executionEvent.orders.singleOrNull { it.parentExternalId == "order1" })

        val eventStopOrder = executionEvent.orders.single { it.externalId == "order1" }
        val childLimitOrder = executionEvent.orders.single { it.parentExternalId == "order1" }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(childLimitOrder.externalId, eventStopOrder.childExternalId)
        assertNull(eventStopOrder.parentExternalId)
        assertEquals("10000", eventStopOrder.price)

        assertEquals(OutgoingOrderStatus.MATCHED, childLimitOrder.status)
        assertNull(childLimitOrder.childExternalId)
        assertEquals("10000", childLimitOrder.price)
    }

    @Test
    fun testFullMatchSeveralStopOrdersWithIncomingOrder() {
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 10000.0)

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD",
                type = LimitOrderType.STOP_LIMIT,
                volume = -0.55,
                lowerLimitPrice = 5001.0,
                lowerPrice = 5000.0,
                uid = "StopOrder1")))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD",
                type = LimitOrderType.STOP_LIMIT,
                volume = -0.45,
                lowerLimitPrice = 5000.0,
                lowerPrice = 5000.0,
                uid = "StopOrder2")))

        clearMessageQueues()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD",
                volume = 2.0, price = 5000.0,
                uid = "IncomingLimitOrder")))

        assertStopOrderBookSize("BTCUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 1)
        assertOrderBookSize("BTCUSD", false, 0)

        val cacheIncomingOrder = genericLimitOrderService.getOrder(DEFAULT_BROKER, "IncomingLimitOrder")
        assertNotNull(cacheIncomingOrder)
        assertEquals(OrderStatus.Processing.name, cacheIncomingOrder.status)
        assertEquals(BigDecimal.valueOf(1), cacheIncomingOrder.remainingVolume)

        val dbIncomingOrder = ordersDatabaseAccessorsHolder.primaryAccessor.loadLimitOrders().singleOrNull { it.externalId == "IncomingLimitOrder" }
        assertNotNull(dbIncomingOrder)
        assertEquals(OrderStatus.Processing.name, dbIncomingOrder.status)
        assertEquals(BigDecimal.valueOf(1), dbIncomingOrder.remainingVolume)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.single() as ExecutionEvent
        assertEquals(5, event.orders.size)

        val eventIncomingOrder = event.orders.single { it.externalId == "IncomingLimitOrder" }
        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, eventIncomingOrder.status)
        assertEquals("1", eventIncomingOrder.remainingVolume)
    }

    @Test
    fun testExpiredStopLimitOrder() {
        val order = buildLimitOrder(walletId = "Client1", assetId = "BTCUSD",
                type = LimitOrderType.STOP_LIMIT,
                volume = -1.0,
                lowerLimitPrice = 1.0,
                lowerPrice = 1.0,
                timeInForce = OrderTimeInForce.GTD,
                expiryTime = Date())

        Thread.sleep(10)
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(order))

        assertEquals(0, expiryOrdersQueue.getExpiredOrdersExternalIds(Date()).size)
        assertStopOrderBookSize("BTCUSD", false, 0)
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single().status)
    }

    @Test
    fun testProcessExpiredStopLimitOrder() {
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 1.0)
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD",
                type = LimitOrderType.STOP_LIMIT,
                volume = -1.0,
                lowerLimitPrice = 1.0,
                lowerPrice = 1.0,
                timeInForce = OrderTimeInForce.GTD,
                expiryTime = Date(Date().time + 300))))

        Thread.sleep(500)

        assertEquals(1, expiryOrdersQueue.getExpiredOrdersExternalIds(Date())[DEFAULT_BROKER]!!.size)

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD",
                volume = 1.0, price = 1.0)))

        assertEquals(0, expiryOrdersQueue.getExpiredOrdersExternalIds(Date())[DEFAULT_BROKER]!!.size)
        assertStopOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 1)

        assertEquals(1, clientsEventsQueue.size)
        val stopOrderEvent = clientsEventsQueue.single() as ExecutionEvent
        assertEquals(3, stopOrderEvent.orders.size)
        assertEquals(2, stopOrderEvent.balanceUpdates?.size)

        val eventStopOrder = stopOrderEvent.orders.single { it.orderType == OrderType.STOP_LIMIT }
        val childLimitOrder = stopOrderEvent.orders.single { it.parentExternalId == eventStopOrder.externalId }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, childLimitOrder.status)

        assertBalance("Client1", "BTC", 1.0, 0.0)
    }

    @Test
    fun testProcessImmediateOrCancelStopLimitOrderWithTrades() {
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 0.5)
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD",
                type = LimitOrderType.STOP_LIMIT,
                volume = -1.0,
                lowerLimitPrice = 1.0,
                lowerPrice = 1.0,
                timeInForce = OrderTimeInForce.IOC)))

        clearMessageQueues()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD",
                volume = 0.5, price = 1.0)))

        assertStopOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 0)

        assertEquals(1, clientsEventsQueue.size)

        val event = clientsEventsQueue.single() as ExecutionEvent
        assertEquals(3, event.orders.size)
        assertEquals(4, event.balanceUpdates?.size)

        val eventStopOrder = event.orders.single { it.orderType == OrderType.STOP_LIMIT }
        val childLimitOrder = event.orders.single { it.parentExternalId == eventStopOrder.externalId }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(0, eventStopOrder.trades?.size)

        assertEquals(OutgoingOrderStatus.CANCELLED, childLimitOrder.status)
        assertEquals(1, childLimitOrder.trades?.size)

        assertBalance("Client1", "BTC", 0.5, 0.0)
    }

    @Test
    fun testProcessImmediateOrCancelStopLimitOrderWithoutTrades() {
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 0.5)
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD",
                type = LimitOrderType.STOP_LIMIT,
                volume = -1.0,
                lowerLimitPrice = 0.99,
                lowerPrice = 0.98,
                timeInForce = OrderTimeInForce.IOC)))

        clearMessageQueues()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD",
                volume = 0.5, price = 0.97)))

        assertStopOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 1)

        assertEquals(1, clientsEventsQueue.size)

        val event = clientsEventsQueue.single() as ExecutionEvent
        assertEquals(3, event.orders.size)
        assertEquals(2, event.balanceUpdates?.size)

        val eventStopOrder = event.orders.single { it.orderType == OrderType.STOP_LIMIT }
        val childLimitOrder = event.orders.single { it.parentExternalId == eventStopOrder.externalId }
        assertEquals(OutgoingOrderStatus.EXECUTED, eventStopOrder.status)
        assertEquals(0, eventStopOrder.trades?.size)

        assertEquals(OrderType.LIMIT, childLimitOrder.orderType)
        assertEquals(OutgoingOrderStatus.CANCELLED, childLimitOrder.status)
        assertEquals(0, childLimitOrder.trades?.size)

        assertBalance("Client1", "BTC", 1.0, 0.0)
    }

}