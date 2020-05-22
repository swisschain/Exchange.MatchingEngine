package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.grpc.TestMarketStreamObserver
import com.swisschain.matching.engine.grpc.TestMultiStreamObserver
import com.swisschain.matching.engine.grpc.TestStreamObserver
import com.swisschain.matching.engine.holders.OrderBookMaxTotalSizeHolder
import com.swisschain.matching.engine.holders.OrderBookMaxTotalSizeHolderImpl
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrderWrapper
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMultiLimitOrderWrapper
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
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (OrderBookMaxSizeTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderBookMaxSizeTest : AbstractTest() {

    @TestConfiguration
    class Config {
        companion object {
            private const val MAX_SIZE = 3
        }

        @Bean
        @Primary
        fun orderBookMaxTotalSizeHolder(): OrderBookMaxTotalSizeHolder {
            return OrderBookMaxTotalSizeHolderImpl(MAX_SIZE)
        }
    }

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Before
    fun setUp() {
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))

        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "BTC", balance = 1.0)
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "EUR", balance = 2000.0)
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 2000.0)
    }

    private fun setMaxSizeOrderBook() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(
                buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = -0.1, price = 5000.0)
        ))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(
                buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = 0.1,
                        type = LimitOrderType.STOP_LIMIT,
                        lowerLimitPrice = 1000.0, lowerPrice = 1000.0)
        ))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(
                buildLimitOrder(walletId = 1, assetId = "EURUSD", volume = -100.0, price = 2.0)
        ))

        assertEquals(2, genericLimitOrderService.searchOrders(DEFAULT_BROKER, 1, null, null).size)
        assertEquals(1, genericStopLimitOrderService.searchOrders(DEFAULT_BROKER, 1, null, null).size)
        clearMessageQueues()
    }

    @Test
    fun testLimitOrder() {
        setMaxSizeOrderBook()

        val messageWrapper = messageBuilder.buildLimitOrderWrapper(
                buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = -0.1, price = 5000.0)
        )
        singleLimitOrderService.processMessage(messageWrapper)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(0, event.balanceUpdates?.size)
        assertEquals(1, event.orders.size)
        assertEquals(OrderStatus.REJECTED, event.orders.single().status)
        assertEquals(OrderRejectReason.ORDER_BOOK_MAX_SIZE_REACHED, event.orders.single().rejectReason)

        val clientHandler = messageWrapper.callback as TestStreamObserver
        assertEquals(1, clientHandler.responses.size)
        val response = clientHandler.responses.single()
        assertEquals(MessageStatus.RUNTIME.type, response.statusValue)
    }

    @Test
    fun testStopLimitOrder() {
        setMaxSizeOrderBook()

        val messageWrapper = messageBuilder.buildLimitOrderWrapper(
                buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = 0.1,
                        type = LimitOrderType.STOP_LIMIT,
                        lowerLimitPrice = 1000.0, lowerPrice = 1000.0)
        )
        singleLimitOrderService.processMessage(messageWrapper)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(0, event.balanceUpdates?.size)
        assertEquals(1, event.orders.size)
        assertEquals(OrderStatus.REJECTED, event.orders.single().status)
        assertEquals(OrderRejectReason.ORDER_BOOK_MAX_SIZE_REACHED, event.orders.single().rejectReason)

        val clientHandler = messageWrapper.callback as TestStreamObserver
        assertEquals(1, clientHandler.responses.size)
        val response = clientHandler.responses.single()
        assertEquals(MessageStatus.RUNTIME.type, response.statusValue)
    }

    @Test
    fun testMultiLimitOrder() {
        setMaxSizeOrderBook()

        val messageWrapper = buildMultiLimitOrderWrapper(
                "EURUSD", 1, listOf(IncomingLimitOrder(-200.0, 3.0, "order1"),
                IncomingLimitOrder(-200.0, 3.1, id = "order2")))
        multiLimitOrderService.processMessage(messageWrapper)


        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.balanceUpdates?.size)
        assertEquals(3, event.orders.size)
        assertNotEquals(OrderStatus.REJECTED, event.orders.single { it.externalId == "order1" }.status)
        assertEquals(OrderStatus.REJECTED, event.orders.single { it.externalId == "order2" }.status)
        assertEquals(OrderRejectReason.ORDER_BOOK_MAX_SIZE_REACHED, event.orders.single { it.externalId == "order2" }.rejectReason)

        val clientHandler = messageWrapper.callback as TestMultiStreamObserver
        assertEquals(1, clientHandler.responses.size)
        val response = clientHandler.responses.single()
        assertEquals(MessageStatus.OK.type, response.statusValue)
        assertNotEquals(MessageStatus.RUNTIME.type, response.statusesList.single { it.id == "order1" }.statusValue)
        assertEquals(MessageStatus.RUNTIME.type, response.statusesList.single { it.id == "order2" }.statusValue)
    }

    @Test
    fun testMarketOrder() {
        setMaxSizeOrderBook()

        val messageWrapper = buildMarketOrderWrapper(
                buildMarketOrder(walletId = 1, assetId = "BTCUSD", volume = 0.1)
        )
        marketOrderService.processMessage(messageWrapper)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.size)
        assertNotEquals(OrderRejectReason.ORDER_BOOK_MAX_SIZE_REACHED, event.orders.single().rejectReason)

        val clientHandler = messageWrapper.callback as TestMarketStreamObserver
        assertEquals(1, clientHandler.responses.size)
        val response = clientHandler.responses.single()
        assertNotEquals(MessageStatus.RUNTIME.type, response.statusValue)
    }
}