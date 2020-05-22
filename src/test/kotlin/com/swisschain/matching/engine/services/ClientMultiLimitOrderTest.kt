package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeSizeType
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.fee.v2.NewLimitOrderFeeInstruction
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.order.OrderCancelMode
import com.swisschain.matching.engine.outgoing.messages.v2.enums.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrderFeeInstructions
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (ClientMultiLimitOrderTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClientMultiLimitOrderTest : AbstractTest() {


    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))

            return testDictionariesDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Autowired
    private lateinit var testSettingDatabaseAccessor: TestSettingsDatabaseAccessor

    @Before
    fun setUp() {
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))

        testBalanceHolderWrapper.updateBalance(1, "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance(1, "USD", 3000.0)
        testBalanceHolderWrapper.updateBalance(2, "EUR", 1000.0)
        testBalanceHolderWrapper.updateBalance(2, "USD", 1000.0)

        initServices()
    }

    @Test
    fun testUnknownAssetPair() {
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("UnknownAssetPair", 1,
                listOf(IncomingLimitOrder(-0.1, 10000.0),
                        IncomingLimitOrder(-0.1, 11000.0),
                        IncomingLimitOrder(0.1, 9000.0),
                        IncomingLimitOrder(0.1, 8000.0))))
        assertEquals(0, clientsEventsQueue.size)
        assertEquals(0, trustedClientsEventsQueue.size)
        assertOrderBookSize("UnknownAssetPair", false, 0)
        assertOrderBookSize("UnknownAssetPair", true, 0)
    }

    @Test
    fun testAdd() {
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1,
                listOf(
                        IncomingLimitOrder(-0.1, 10000.0, "1"),
                        IncomingLimitOrder(-0.2, 10500.0, "2"),
                        IncomingLimitOrder(-0.30000001, 11000.0, "3"),
                        IncomingLimitOrder(0.1, 9500.0, "4"),
                        IncomingLimitOrder(0.2, 9000.0, "5")
                )))

        assertOrderBookSize("BTCUSD", false, 3)
        assertOrderBookSize("BTCUSD", true, 2)

        assertEquals(2, outgoingOrderBookQueue.size)

        assertBalance(1, "BTC", 1.0, 0.60000001)
        assertBalance(1, "USD", 3000.0, 2750.0)

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(0, trustedClientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "BTC", "1", "1", "0", "0.60000001", event.balanceUpdates!!)
        assertEventBalanceUpdate(1, "USD", "3000", "3000", "0", "2750", event.balanceUpdates!!)
        assertEquals(5, event.orders.size)
        event.orders.forEach {
            assertEquals(OutgoingOrderStatus.PLACED, it.status)
            assertEquals(0, it.trades?.size)
        }
        assertEquals("-0.1", event.orders.single { it.externalId == "1" }.remainingVolume)
    }

    @Test
    fun testAddOneSide() {
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1,
                listOf(
                        IncomingLimitOrder(-0.1, 10000.0),
                        IncomingLimitOrder(-0.2, 10500.0)
                )))

        assertOrderBookSize("BTCUSD", false, 2)
        assertOrderBookSize("BTCUSD", true, 0)

        assertEquals(1, outgoingOrderBookQueue.size)

        assertBalance(1, "BTC", 1.0, 0.3)
        assertBalance(1, "USD", 3000.0, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(0, trustedClientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.orders.size)
        assertEquals(1, event.balanceUpdates?.size)
    }

    @Test
    fun testCancelAllPrevious() {
        testBalanceHolderWrapper.updateBalance(2, "BTC", 0.2)
        initServices()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 2, assetId = "BTCUSD", price = 10500.0, volume = -0.2)))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "ForCancel-1", walletId = 1, assetId = "BTCUSD", price = 10100.0, volume = -0.4)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "ForCancel-2", walletId = 1, assetId = "BTCUSD", price = 11000.0, volume = -0.3)))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "ForCancel-3", walletId = 1, assetId = "BTCUSD", price = 9000.0, volume = 0.1)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "ForCancel-4", walletId = 1, assetId = "BTCUSD", price = 8000.0, volume = 0.2)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "ForCancel-5", walletId = 1, assetId = "BTCUSD", price = 7000.0, volume = 0.001)))

        clearMessageQueues()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1,
                listOf(
                        IncomingLimitOrder(-0.1, 10000.0),
                        IncomingLimitOrder(-0.2, 10500.0),
                        IncomingLimitOrder(-0.30000001, 11000.0),
                        IncomingLimitOrder(0.1, 9500.0),
                        IncomingLimitOrder(0.2, 9000.0)
                )))

        assertOrderBookSize("BTCUSD", false, 4)
        assertOrderBookSize("BTCUSD", true, 2)

        assertEquals(2, outgoingOrderBookQueue.size)

        val buyOrderBook = outgoingOrderBookQueue.first { it.orderBook.isBuy }
        val sellOrderBook = outgoingOrderBookQueue.first { !it.orderBook.isBuy }
        assertEquals(2, buyOrderBook.orderBook.prices.size)
        assertEquals(BigDecimal.valueOf(9500.0), buyOrderBook.orderBook.prices.first().price)
        assertEquals(4, sellOrderBook.orderBook.prices.size)
        assertEquals(BigDecimal.valueOf(10000.0), sellOrderBook.orderBook.prices.first().price)

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(0, trustedClientsEventsQueue.size)

        assertBalance(1, "BTC", 1.0, 0.60000001)
        assertBalance(1, "USD", 3000.0, 2750.0)
        assertBalance(2, "BTC", 0.2, 0.2)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.balanceUpdates?.size)
        assertEquals(10, event.orders.size)
        val eventCancelledIds = event.orders.filter { it.status == OutgoingOrderStatus.CANCELLED }.map { it.externalId }.toMutableList()
        eventCancelledIds.sort()
        assertEquals(listOf("ForCancel-1", "ForCancel-2", "ForCancel-3", "ForCancel-4", "ForCancel-5"), eventCancelledIds)
    }

    @Test
    fun testCancelAllPreviousOneSide() {

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "ForCancel-1", walletId = 1, assetId = "BTCUSD", price = 10100.0, volume = -0.4)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "ForCancel-2", walletId = 1, assetId = "BTCUSD", price = 11000.0, volume = -0.3)))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCUSD", price = 9000.0, volume = 0.1)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCUSD", price = 8000.0, volume = 0.2)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCUSD", price = 7000.0, volume = 0.001)))

        clearMessageQueues()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1,
                listOf(
                        IncomingLimitOrder(-0.1, 10000.0),
                        IncomingLimitOrder(-0.2, 10500.0),
                        IncomingLimitOrder(-0.30000001, 11000.0)
                )))

        assertOrderBookSize("BTCUSD", false, 3)
        assertOrderBookSize("BTCUSD", true, 3)

        assertEquals(1, outgoingOrderBookQueue.size)

        val sellOrderBook = outgoingOrderBookQueue.first { !it.orderBook.isBuy }
        assertEquals(3, sellOrderBook.orderBook.prices.size)
        assertEquals(BigDecimal.valueOf(10000.0), sellOrderBook.orderBook.prices.first().price)

        assertBalance(1, "BTC", 1.0, 0.60000001)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(5, event.orders.size)
        val eventCancelledIds = event.orders.filter { it.status == OutgoingOrderStatus.CANCELLED }.map { it.externalId }.toMutableList()
        eventCancelledIds.sort()
        assertEquals(listOf("ForCancel-1", "ForCancel-2"), eventCancelledIds)
    }

    @Test
    fun testAddNotEnoughFundsOrder() {
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1,
                listOf(
                        IncomingLimitOrder(-0.1, 10000.0, "1"),
                        IncomingLimitOrder(-0.2, 10500.0, "2"),
                        IncomingLimitOrder(-0.30000001, 11000.0, "3"),
                        IncomingLimitOrder(-0.4, 12000.0, "ToReject-1"),
                        IncomingLimitOrder(0.1, 9500.0, "5"),
                        IncomingLimitOrder(0.2, 9000.0, "6"),
                        IncomingLimitOrder(0.03, 9500.0, "ToReject-2")
                )))

        assertOrderBookSize("BTCUSD", false, 3)
        assertOrderBookSize("BTCUSD", true, 2)

        assertEquals(2, outgoingOrderBookQueue.size)

        assertBalance(1, "BTC", 1.0, 0.60000001)
        assertBalance(1, "USD", 3000.0, 2750.0)

        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.ORDER, event.header.messageType)
        assertEquals(7, event.orders.size)
        assertEquals(OutgoingOrderStatus.REJECTED, event.orders.single { it.externalId == "ToReject-1" }.status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, event.orders.single { it.externalId == "ToReject-1" }.rejectReason)
        assertEquals(OutgoingOrderStatus.REJECTED, event.orders.single { it.externalId == "ToReject-2" }.status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, event.orders.single { it.externalId == "ToReject-2" }.rejectReason)
    }

    @Test
    fun testMatch() {
        testSettingDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("100"))

        testBalanceHolderWrapper.updateBalance(1, "USD", 10000.0)

        testBalanceHolderWrapper.updateBalance(2, "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance(2, "USD", 10000.0)

        testBalanceHolderWrapper.updateBalance(3, "BTC", 0.2)

        testBalanceHolderWrapper.updateBalance(100, "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance(100, "USD", 3000.0)

        initServices()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 100, listOf(
                IncomingLimitOrder(-0.3, 10800.0, "3"),
                IncomingLimitOrder(-0.4, 10900.0, "2"),
                IncomingLimitOrder(0.1, 9500.0, "6"),
                IncomingLimitOrder(0.2, 9300.0, "7")
        )))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "ToCancelDueToNoFundsForFee", walletId = 3, assetId = "BTCUSD", volume = -0.2, price = 10500.0,
                fees = buildLimitOrderFeeInstructions(FeeType.CLIENT_FEE, makerSize = 0.05, targetWalletId = 200, assetIds = listOf("BTC"))
        )))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 2, listOf(
                IncomingLimitOrder(-0.1, 10000.0, "5"),
                IncomingLimitOrder(-0.5, 11000.0, "1"),
                IncomingLimitOrder(0.3, 9000.0, "8"),
                IncomingLimitOrder(0.4, 8800.0, "9")
        )))

        clearMessageQueues()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1, listOf(
                IncomingLimitOrder(-0.1, 11500.0, "14"),
                IncomingLimitOrder(0.05, 11000.0, "12"),
                IncomingLimitOrder(0.2, 10800.0, "13"),
                IncomingLimitOrder(0.1, 9900.0, "11")
        )))


        assertOrderBookSize("BTCUSD", false, 4)
        assertOrderBookSize("BTCUSD", true, 5)

        assertBalance(1, "BTC", 1.25, 0.1)
        assertBalance(1, "USD", 7380.0, 990.0)
        assertBalance(3, "BTC", 0.2, 0.0)

        assertBalance(2, "BTC", 0.9, 0.5)
        assertBalance(2, "USD", 11000.0, 6220.0)

        assertBalance(100, "BTC", 0.85, 0.0)
        assertBalance(100, "USD", 4620.0, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(0, trustedClientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(7, event.orders.size)

        val eventOrderIds = event.orders.map { it.externalId }.toMutableList()
        eventOrderIds.sort()
        assertEquals(listOf("11", "12", "13", "14", "3", "5", "ToCancelDueToNoFundsForFee"), eventOrderIds)

        val eventMatchedIds = event.orders.filter { it.status == OutgoingOrderStatus.MATCHED }.map { it.externalId }.toMutableList()
        eventMatchedIds.sort()
        assertEquals(listOf("12", "13", "5"), eventMatchedIds)

        val eventCancelledIds = event.orders.filter { it.status == OutgoingOrderStatus.CANCELLED }.map { it.externalId }.toMutableList()
        eventCancelledIds.sort()
        assertEquals(listOf("ToCancelDueToNoFundsForFee"), eventCancelledIds)

        val eventAddedIds = event.orders.filter { it.status == OutgoingOrderStatus.PLACED }.map { it.externalId }.toMutableList()
        eventAddedIds.sort()
        assertEquals(listOf("11", "14"), eventAddedIds)

        val eventPartiallyMatchedIds = event.orders.filter { it.status == OutgoingOrderStatus.PARTIALLY_MATCHED }.map { it.externalId }.toMutableList()
        eventPartiallyMatchedIds.sort()
        assertEquals(listOf("3"), eventPartiallyMatchedIds)
    }

    @Test
    fun testNegativeSpread() {
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1, listOf(
                IncomingLimitOrder(-0.1, 10000.0),
                IncomingLimitOrder(0.1, 10100.0)
        )))

        assertOrderBookSize("BTCUSD", false, 1)
        assertOrderBookSize("BTCUSD", true, 0)

        assertBalance(1, "BTC", 1.0, 0.1)
        assertBalance(1, "USD", 3000.0, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "BTC", "1", "1", "0", "0.1", event.balanceUpdates!!)
    }

    @Test
    fun testCancelPreviousAndMatch() {
        testBalanceHolderWrapper.updateBalance(2, "BTC", 0.3)
        testBalanceHolderWrapper.updateBalance(1, "USD", 2400.0)
        testBalanceHolderWrapper.updateBalance(1, "BTC", 0.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 2, assetId = "BTCUSD", volume = -0.3, price = 9500.0)))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1, listOf(
                IncomingLimitOrder(0.1, 9000.0),
                IncomingLimitOrder(0.1, 8000.0),
                IncomingLimitOrder(0.1, 7000.0)
        )))

        clearMessageQueues()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1, listOf(
                IncomingLimitOrder(0.1, 10000.0),
                IncomingLimitOrder(0.01, 9500.0),
                IncomingLimitOrder(0.1, 9000.0),
                IncomingLimitOrder(0.1, 8000.0)
        )))

        assertOrderBookSize("BTCUSD", false, 1)
        assertOrderBookSize("BTCUSD", true, 1)

        assertEquals(BigDecimal.valueOf(9000.0), genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getBidPrice())
        assertEquals(BigDecimal.valueOf(9500.0), genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getAskPrice())

        assertBalance(1, "USD", 1355.0, 900.0)
        assertBalance(1, "BTC", 0.11, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(8, (clientsEventsQueue.first() as ExecutionEvent).orders.size)
    }

    private fun setOrder() {
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1, listOf(
                IncomingLimitOrder(-0.4, 9200.0),
                IncomingLimitOrder(-0.3, 9100.0),
                IncomingLimitOrder(-0.2, 9000.0),
                IncomingLimitOrder(0.2, 7900.0),
                IncomingLimitOrder(0.1, 7800.0)
        )))
        clearMessageQueues()
    }

    @Test
    fun testEmptyOrderWithCancelPreviousBothSides() {
        setOrder()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1, orders = emptyList(),
                cancel = true, cancelMode = OrderCancelMode.BOTH_SIDES))

        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(5, event.orders.size)
        event.orders.forEach {
            assertEquals(OutgoingOrderStatus.CANCELLED, it.status)
        }
    }

    @Test
    fun testOneSideOrderWithCancelPreviousBothSides() {
        setOrder()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1,
                listOf(IncomingLimitOrder(-0.4, 9100.0, "1"),
                        IncomingLimitOrder(-0.3, 9000.0, "2")),
                cancel = true, cancelMode = OrderCancelMode.BOTH_SIDES))

        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 2)

        assertTrue(genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(false).map { it.externalId }.containsAll(listOf("1", "2")))

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(7, (clientsEventsQueue.poll() as ExecutionEvent).orders.size)
    }

    @Test
    fun testBothSidesOrderWithCancelPreviousOneSide() {
        setOrder()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1,
                listOf(IncomingLimitOrder(-0.01, 9100.0, "1"),
                        IncomingLimitOrder(-0.009, 9000.0, "2"),
                        IncomingLimitOrder(0.2, 7900.0, "3")),
                cancel = true, cancelMode = OrderCancelMode.BUY_SIDE))

        assertOrderBookSize("BTCUSD", true, 1)
        assertOrderBookSize("BTCUSD", false, 5)

        assertEquals(genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCUSD").getOrderBook(true).map { it.externalId }, listOf("3"))

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(5, (clientsEventsQueue.poll() as ExecutionEvent).orders.size)
    }

    @Test
    fun testReplaceOrders() {
        testBalanceHolderWrapper.updateBalance(2, "BTC", 0.1)
        testBalanceHolderWrapper.updateReservedBalance(2, "BTC", 0.1)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "ClientOrder", walletId = 2, assetId = "BTCUSD", volume = -0.1, price = 8000.0))
        initServices()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1, listOf(
                IncomingLimitOrder(-0.4, 9300.0, "Ask-ToReplace-2"),
                IncomingLimitOrder(-0.3, 9200.0, "Ask-ToReplace-1"),
                IncomingLimitOrder(-0.2, 9100.0, "Ask-ToCancel-2"),
                IncomingLimitOrder(-0.1, 9000.0, "Ask-ToCancel-1"),
                IncomingLimitOrder(0.2, 7900.0, "Bid-ToReplace-1"),
                IncomingLimitOrder(0.1, 7800.0, "Bid-ToCancel-1"),
                IncomingLimitOrder(0.05, 7700.0, "Bid-ToReplace-2")
        )))
        clearMessageQueues()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 1, listOf(
                IncomingLimitOrder(-0.2, 9400.0, "NotFoundPrevious-1", oldUid = "NotExist-1"),
                IncomingLimitOrder(-0.2, 9300.0, "ask2", oldUid = "Ask-ToReplace-2"),
                IncomingLimitOrder(-0.3, 9200.0, "ask3", oldUid = "Ask-ToReplace-1"),
                IncomingLimitOrder(-0.2, 9100.0, "ask4"),
                IncomingLimitOrder(-0.3001, 9000.0, "NotEnoughFunds"),
                IncomingLimitOrder(0.11, 8000.0, "bid1", oldUid = "Bid-ToReplace-1"),
                IncomingLimitOrder(0.1, 7900.0, "bid2", oldUid = "Bid-ToReplace-2"),
                IncomingLimitOrder(0.1, 7800.0, "NotFoundPrevious-2", oldUid = "NotExist-2"),
                IncomingLimitOrder(0.05, 7700.0, "bid4")
        ), cancel = true))

        assertOrderBookSize("BTCUSD", true, 3)
        assertOrderBookSize("BTCUSD", false, 3)

        assertBalance(1, "BTC", 1.1, 0.7)
        assertBalance(1, "USD", 2200.0, 1255.0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(17, event.orders.size)

        val eventReplacedOrders = event.orders.filter { it.status == OutgoingOrderStatus.REPLACED }
        assertEquals(4, eventReplacedOrders.size)
        assertTrue(listOf("Ask-ToReplace-1", "Ask-ToReplace-2", "Bid-ToReplace-1", "Bid-ToReplace-2")
                .containsAll(eventReplacedOrders.map { it.externalId }))

        val eventNotFoundPreviousOrders = event.orders.filter { it.status == OutgoingOrderStatus.REJECTED && it.rejectReason == OrderRejectReason.NOT_FOUND_PREVIOUS }
        assertEquals(2, eventNotFoundPreviousOrders.size)
        assertTrue(listOf("NotFoundPrevious-1", "NotFoundPrevious-2").containsAll(eventNotFoundPreviousOrders.map { it.externalId }))

        val eventNotEnoughFundsOrders = event.orders.filter { it.status == OutgoingOrderStatus.REJECTED && it.rejectReason == OrderRejectReason.NOT_ENOUGH_FUNDS }
        assertEquals(1, eventNotEnoughFundsOrders.size)
        assertTrue(listOf("NotEnoughFunds").containsAll(eventNotEnoughFundsOrders.map { it.externalId }))

        val evevntMatchedOrders = event.orders.filter { it.status == OutgoingOrderStatus.MATCHED }
        assertEquals(1, evevntMatchedOrders.size)
        assertTrue(listOf("ClientOrder").containsAll(evevntMatchedOrders.map { it.externalId }))

        val eventProcessedOrders = event.orders.filter { it.status == OutgoingOrderStatus.PARTIALLY_MATCHED }
        assertEquals(1, eventProcessedOrders.size)
        assertTrue(listOf("bid1").containsAll(eventProcessedOrders.map { it.externalId }))

        val eventInOrderBookOrders = event.orders.filter { it.status == OutgoingOrderStatus.PLACED }
        assertEquals(5, eventInOrderBookOrders.size)
        assertTrue(listOf("ask2", "ask3", "ask4", "bid2", "bid4").containsAll(eventInOrderBookOrders.map { it.externalId }))

        val eventCancelledOrders = event.orders.filter { it.status == OutgoingOrderStatus.CANCELLED }
        assertEquals(3, eventCancelledOrders.size)
        assertTrue(listOf("Ask-ToCancel-1", "Ask-ToCancel-2", "Bid-ToCancel-1").containsAll(eventCancelledOrders.map { it.externalId }))
    }


    @Test
    fun testAddLimitOrderWithSameReserveSum() {
        //Do not send balance update if balances didn't change
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("EURUSD", 2, listOf(
                IncomingLimitOrder(100.0, 1.2, "1"),
                IncomingLimitOrder(100.0, 1.3, "2")
        )))

        assertEquals(BigDecimal.valueOf(1000.0), testWalletDatabaseAccessor.getBalance(2, "USD"))
        assertEquals(BigDecimal.valueOf(250.0), testWalletDatabaseAccessor.getReservedBalance(2, "USD"))

        assertEquals(1, clientsEventsQueue.size)
        var event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.orders.size)
        assertEquals("1.2", event.orders[0].price)
        assertEquals("1.3", event.orders[1].price)
        assertEquals(1, event.balanceUpdates?.size)


        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("EURUSD", 2, listOf(
                IncomingLimitOrder(100.0, 1.2, "3", oldUid = "1"),
                IncomingLimitOrder(100.0, 1.3, "4", oldUid = "2")
        )))

        assertEquals(1, clientsEventsQueue.size)
        event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(4, event.orders.size)
        assertEquals("1.2", event.orders[2].price)
        assertEquals("1.3", event.orders[3].price)
        assertEquals(0, event.balanceUpdates?.size)
    }

    @Test
    fun testReplaceOrderWithAnotherPairAndClient() {
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "orderWithAnotherPair", assetId = "EURUSD", walletId = 1,
                volume = 1.0, price = 1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(uid = "orderWithAnotherClient", assetId = "BTCUSD", walletId = 2,
                volume = 1.0, price = 1.0))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper(pair = "BTCUSD", walletId = 1,
                orders = listOf(IncomingLimitOrder(oldUid = "orderWithAnotherPair", volume = 1.1, price = 1.0),
                        IncomingLimitOrder(oldUid = "orderWithAnotherClient", volume = 1.1, price = 1.0)), cancel = false))

        assertOrderBookSize("EURUSD", true, 1)
        assertOrderBookSize("BTCUSD", true, 1)

        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.orders.size)
        assertEquals(OutgoingOrderStatus.REJECTED, event.orders[0].status)
        assertEquals(OrderRejectReason.NOT_FOUND_PREVIOUS, event.orders[0].rejectReason)
        assertEquals(OutgoingOrderStatus.REJECTED, event.orders[1].status)
        assertEquals(OrderRejectReason.NOT_FOUND_PREVIOUS, event.orders[1].rejectReason)
    }
//
//    @Test
//    fun testComplexMultiLimitOrder() {
//        testBalanceHolderWrapper.updateBalance(1, "USD", 100.0)
//        testBalanceHolderWrapper.updateReservedBalance(1, "USD", 100.0)
//        testBalanceHolderWrapper.updateBalance(1, "BTC", 2.0)
//        testBalanceHolderWrapper.updateReservedBalance(1, "BTC", 2.0)
//
//        testBalanceHolderWrapper.updateBalance(2, "BTC", 1.0)
//        testBalanceHolderWrapper.updateReservedBalance(2, "BTC", 1.0)
//        testBalanceHolderWrapper.updateBalance(2, "USD", 0.0)
//        testBalanceHolderWrapper.updateReservedBalance(2, "USD", 0.0)
//
//        testBalanceHolderWrapper.updateBalance(3, "BTC", 0.0)
//        testBalanceHolderWrapper.updateReservedBalance(3, "BTC", 0.0)
//        testBalanceHolderWrapper.updateBalance(3, "USD", 730.0)
//        testBalanceHolderWrapper.updateReservedBalance(3, "USD", 730.0)
//
//        testBalanceHolderWrapper.updateBalance(4, "BTC", 2.0)
//        testBalanceHolderWrapper.updateReservedBalance(4, "BTC", 2.0)
//        testBalanceHolderWrapper.updateBalance(4, "USD", 530.0)
//        testBalanceHolderWrapper.updateReservedBalance(4, "USD", 301.0)
//
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = 1.0, price = 100.0))
//
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = -1.0, price = 220.0, uid = "LimitOrder-1", reservedVolume = 1.0))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = -1.0, price = 206.0, uid = "LimitOrder-2", reservedVolume = 1.0))
//        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "BTCUSD", volume = -1.0, price = 200.0, uid = "LimitOrder-3", reservedVolume = 1.0))
//
//        testOrderBookWrapper.addStopLimitOrder(buildLimitOrder(walletId = 2, assetId = "BTCUSD", uid = "StopOrder-1",
//                volume = -1.0, type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 102.0, upperPrice = 101.0, reservedVolume = 1.0))
//        testOrderBookWrapper.addStopLimitOrder(buildLimitOrder(walletId = 4, assetId = "BTCUSD", uid = "StopOrder-4",
//                volume = -1.0, type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 110.0, upperPrice = 110.0, reservedVolume = 1.0))
//
//        testOrderBookWrapper.addStopLimitOrder(buildLimitOrder(walletId = 3, assetId = "BTCUSD", uid = "StopOrder-2",
//                volume = 1.0, type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 202.0, upperPrice = 210.0, reservedVolume = 210.0))
//        testOrderBookWrapper.addStopLimitOrder(buildLimitOrder(walletId = 3, assetId = "BTCUSD", uid = "StopOrder-3",
//                volume = 1.0, type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 210.0, upperPrice = 220.0, reservedVolume = 220.0))
//        testOrderBookWrapper.addStopLimitOrder(buildLimitOrder(walletId = 3, assetId = "BTCUSD",
//                volume = 1.0, type = LimitOrderType.STOP_LIMIT, upperLimitPrice = 300.0, upperPrice = 300.0))
//        testOrderBookWrapper.addStopLimitOrder(buildLimitOrder(walletId = 4, assetId = "BTCUSD", uid = "ToReplace",
//                volume = 1.0, type = LimitOrderType.STOP_LIMIT,
//                lowerLimitPrice = 1.0, lowerPrice = 1.0, upperLimitPrice = 301.0, upperPrice = 301.0, reservedVolume = 301.0))
//
//        initServices()
//
//        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper(walletId = 4, pair = "BTCUSD",
//                orders = listOf(IncomingLimitOrder(volume = 2.0, price = -4.0, id = "IncomingInvalidPrice"),
//                        IncomingLimitOrder(volume = 0.5, price = 220.0, id = "Incoming-2")), cancel = true, cancelMode = OrderCancelMode.SELL_SIDE))
//
//        assertStopOrderBookSize("BTCUSD", true, 3)
//        assertOrderBookSize("BTCUSD", true, 1)
//        assertOrderBookSize("BTCUSD", false, 2)
//
//        assertBalance(1, "BTC", 1.0, 1.0)
//        assertBalance(1, "USD", 526.0, 100.0)
//        assertBalance(2, "BTC", 0.0, 0.0)
//        assertBalance(2, "USD", 210.0, 0.0)
//        assertBalance(3, "BTC", 0.5, 0.0)
//        assertBalance(3, "USD", 620.0, 620.0)
//        assertBalance(4, "BTC", 4.5, 0.0)
//        assertBalance(4, "USD", 4.0, 0.0)
//
//        assertEquals(1, clientsEventsQueue.size)
//        val event = clientsEventsQueue.poll() as ExecutionEvent
//
//        assertEquals(8, event.balanceUpdates?.size)
//        assertEventBalanceUpdate(1, "BTC", "2", "0", "2", "0", event.balanceUpdates!!)
//        assertEventBalanceUpdate(1, "USD", "100", "526", "100", "100", event.balanceUpdates!!)
//        assertEventBalanceUpdate(2, "BTC", "1", "0", "1", "0", event.balanceUpdates!!)
//        assertEventBalanceUpdate(2, "USD", "0", "210", "0", "0", event.balanceUpdates!!)
//        assertEventBalanceUpdate(3, "BTC", "0", "0.5", "0", "0", event.balanceUpdates!!)
//        assertEventBalanceUpdate(3, "USD", "730", "620", "730", "620", event.balanceUpdates!!)
//        assertEventBalanceUpdate(4, "BTC", "2", "4.5", "2", "0", event.balanceUpdates!!)
//        assertEventBalanceUpdate(4, "USD", "530", "4", "301", "0", event.balanceUpdates!!)
//
//        assertEquals(15, event.orders.size)
//
//        val stopOrder1Child = event.orders.single { it.parentExternalId == "StopOrder-1" }
//        val stopOrder2Child = event.orders.single { it.parentExternalId == "StopOrder-2" }
//        val stopOrder3Child = event.orders.single { it.parentExternalId == "StopOrder-3" }
//        val incoming1Child = event.orders.single { it.parentExternalId == "Incoming-1" }
//
//        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "LimitOrder-3" }.status)
//        assertEquals(0, event.orders.single { it.externalId == "LimitOrder-3" }.trades?.size)
//
//        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "StopOrder-4" }.status)
//        assertEquals(0, event.orders.single { it.externalId == "StopOrder-4" }.trades?.size)
//
//        assertEquals(OutgoingOrderStatus.REPLACED, event.orders.single { it.externalId == "ToReplace" }.status)
//        assertEquals(0, event.orders.single { it.externalId == "ToReplace" }.trades?.size)
//
//        var order = event.orders.single { it.externalId == "LimitOrder-1" }
//        assertEquals(OutgoingOrderStatus.MATCHED, order.status)
//        assertEquals(2, order.trades?.size)
//        assertEquals("Incoming-2", order.trades!!.single { it.index == 1 }.oppositeExternalOrderId)
//        assertEquals(stopOrder3Child.externalId, order.trades!!.single { it.index == 3 }.oppositeExternalOrderId)
//
//        order = event.orders.single { it.externalId == "LimitOrder-2" }
//        assertEquals(OutgoingOrderStatus.MATCHED, order.status)
//        assertEquals(1, order.trades?.size)
//        assertEquals(incoming1Child.externalId, order.trades!!.single { it.index == 0 }.oppositeExternalOrderId)
//
//        order = event.orders.single { it.externalId == "StopOrder-1" }
//        assertEquals(OutgoingOrderStatus.EXECUTED, order.status)
//        assertEquals(stopOrder1Child.externalId, order.childExternalId)
//        assertEquals(0, order.trades?.size)
//
//        order = stopOrder1Child
//        assertEquals(OutgoingOrderStatus.MATCHED, order.status)
//        assertEquals(1, order.trades?.size)
//        assertEquals(incoming1Child.externalId, order.trades!!.single { it.index == 2 }.oppositeExternalOrderId)
//
//        order = event.orders.single { it.externalId == "StopOrder-2" }
//        assertEquals(OutgoingOrderStatus.EXECUTED, order.status)
//        assertEquals(stopOrder2Child.externalId, order.childExternalId)
//        assertEquals(0, order.trades?.size)
//
//        order = stopOrder2Child
//        assertEquals(OutgoingOrderStatus.PLACED, order.status)
//        assertEquals(0, order.trades?.size)
//
//        order = event.orders.single { it.externalId == "StopOrder-3" }
//        assertEquals(OutgoingOrderStatus.EXECUTED, order.status)
//        assertEquals(stopOrder3Child.externalId, order.childExternalId)
//        assertEquals(0, order.trades?.size)
//
//        order = stopOrder3Child
//        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, order.status)
//        assertEquals(1, order.trades?.size)
//        assertEquals("LimitOrder-1", order.trades!!.single { it.index == 3 }.oppositeExternalOrderId)
//
//        assertEquals(OutgoingOrderStatus.REJECTED, event.orders.single { it.externalId == "IncomingInvalidPrice" }.status)
//        assertEquals(OrderRejectReason.INVALID_PRICE, event.orders.single { it.externalId == "IncomingInvalidPrice" }.rejectReason)
//        assertEquals(0, event.orders.single { it.externalId == "IncomingInvalidPrice" }.trades?.size)
//
//        order = event.orders.single { it.externalId == "Incoming-1" }
//        assertEquals(OutgoingOrderStatus.EXECUTED, order.status)
//        assertEquals(incoming1Child.externalId, order.childExternalId)
//        assertEquals(0, order.trades?.size)
//
//        order = incoming1Child
//        assertEquals(OutgoingOrderStatus.MATCHED, order.status)
//        assertEquals(2, order.trades?.size)
//        assertEquals("LimitOrder-2", order.trades!!.single { it.index == 0 }.oppositeExternalOrderId)
//        assertEquals(stopOrder1Child.externalId, order.trades!!.single { it.index == 2 }.oppositeExternalOrderId)
//
//        order = event.orders.single { it.externalId == "Incoming-2" }
//        assertEquals(OutgoingOrderStatus.MATCHED, order.status)
//        assertEquals(1, order.trades?.size)
//        assertEquals("LimitOrder-1", order.trades!!.single { it.index == 1 }.oppositeExternalOrderId)
//
//    }

    @Test
    fun testCancelPartiallyMatchedOrderAfterRejectedIncomingOrder() {
        testSettingDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("100"))
        applicationSettingsCache.update()

        testBalanceHolderWrapper.updateBalance(1, "USD", 0.0)
        testBalanceHolderWrapper.updateBalance(2, "EUR", 0.0)

        testBalanceHolderWrapper.updateBalance(1, "EUR", 5.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "EUR", 5.0)

        testBalanceHolderWrapper.updateBalance(100, "EUR", 4.0)

        testBalanceHolderWrapper.updateBalance(2, "USD", 100.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 100, assetId = "EURUSD", volume = -9.0, price = 1.1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "EURUSD", volume = -5.0, price = 1.2))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("EURUSD", 2,
                listOf(IncomingLimitOrder(volume = 1.0, price = 1.4, id = "matched1"),
                        IncomingLimitOrder(volume = 3.0, price = 1.3, id = "matched2"),
                        IncomingLimitOrder(volume = 5.0, price = 1.2, id = "rejectedAfterMatching",
                                // 'not enough funds' fee to cancel this order during matching
                                feeInstructions = listOf(NewLimitOrderFeeInstruction(FeeType.CLIENT_FEE, FeeSizeType.PERCENTAGE, BigDecimal.valueOf(0.1), null, null,  null, null, null, 500, listOf("BTC"), null))
                        ))))


        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 0)

        assertBalance(1, "EUR", 5.0, 5.0)
        assertBalance(1, "USD", 0.0, 0.0)

        assertBalance(2, "EUR", 4.0, 0.0)
        assertBalance(2, "USD", 95.6, 0.0)

        assertBalance(100, "EUR", 0.0, 0.0)
        assertBalance(100, "USD", 4.4, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent

        assertEquals(4, event.orders.size)
        assertEquals(4, event.balanceUpdates?.size)

        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "matched1" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "matched2" }.status)
        assertEquals(OutgoingOrderStatus.REJECTED, event.orders.single { it.externalId == "rejectedAfterMatching" }.status)

        val trustedClientOrder = event.orders.single { it.walletId == 100L }
        assertEquals(OutgoingOrderStatus.CANCELLED, trustedClientOrder.status)
        assertEquals(2, trustedClientOrder.trades?.size)
        assertEquals("-5", trustedClientOrder.remainingVolume)
    }

    @Test
    fun testCancelPartiallyMatchedOrderDueToNotEnoughFunds() {
        testSettingDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("100"))
        applicationSettingsCache.update()

        testBalanceHolderWrapper.updateBalance(1, "USD", 0.0)
        testBalanceHolderWrapper.updateBalance(2, "EUR", 0.0)

        testBalanceHolderWrapper.updateBalance(1, "EUR", 8.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "EUR", 8.0)

        testBalanceHolderWrapper.updateBalance(100, "EUR", 5.0)

        testBalanceHolderWrapper.updateBalance(2, "USD", 100.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 100, assetId = "EURUSD", volume = -9.0, price = 1.1))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "EURUSD", volume = -5.0, price = 1.2))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "EURUSD", volume = -3.0, price = 1.4))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("EURUSD", 2,
                listOf(IncomingLimitOrder(volume = 1.0, price = 1.4),
                        IncomingLimitOrder(volume = 3.0, price = 1.3),
                        IncomingLimitOrder(volume = 5.0, price = 1.2))))

        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 0)

        assertBalance(1, "EUR", 3.0, 3.0)
        assertBalance(1, "USD", 6.0, 0.0)

        assertBalance(2, "EUR", 9.0, 0.0)
        assertBalance(2, "USD", 89.6, 0.0)

        assertBalance(100, "EUR", 1.0, 0.0)
        assertBalance(100, "USD", 4.4, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent

        assertEquals(5, event.orders.size)
        assertEquals(6, event.balanceUpdates?.size)

        assertEquals(3, event.orders.filter { it.walletId == 2L }.size)
        event.orders.filter { it.walletId == 2L }.forEach {
            assertEquals(OutgoingOrderStatus.MATCHED, it.status)
        }

        val trustedClientOrder = event.orders.single { it.walletId == 100L }
        assertEquals(OutgoingOrderStatus.CANCELLED, trustedClientOrder.status)
        assertEquals(2, trustedClientOrder.trades?.size)
        assertEquals("-5", trustedClientOrder.remainingVolume)

        val clientOrder = event.orders.single { it.walletId == 1L }
        assertEquals(OutgoingOrderStatus.MATCHED, clientOrder.status)
    }
}