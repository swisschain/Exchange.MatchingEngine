
package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMultiLimitOrderWrapper
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
import kotlin.test.assertEquals
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus


@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (LimitOrderMassCancelServiceTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LimitOrderMassCancelServiceTest : AbstractTest() {
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()

            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))

            return testDictionariesDatabaseAccessor
        }

        @Bean
        @Primary
        fun testConfig(): TestSettingsDatabaseAccessor {
            val testSettingsDatabaseAccessor = TestSettingsDatabaseAccessor()
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("100"))
            return testSettingsDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Before
    fun setUp() {

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2))

        testBalanceHolderWrapper.updateBalance(1, "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance(1, "USD", 100.0)
        testBalanceHolderWrapper.updateBalance(100, "EUR", 10.0)
        testBalanceHolderWrapper.updateBalance(100, "USD", 10.0)
        testBalanceHolderWrapper.updateBalance(100, "BTC", 1.0)

        initServices()
    }

    private fun setOrders() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "1", walletId = 1, assetId = "BTCUSD", volume = -0.5, price = 9000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "2", walletId = 1, assetId = "BTCUSD", volume = -0.1, price = 9000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "3", walletId = 1, assetId = "BTCUSD", volume = 0.01, price = 7000.0)))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "4", walletId = 1, assetId = "EURUSD", volume = 10.0, price = 1.1)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "5",
                walletId = 1,
                assetId = "BTCUSD",
                type = LimitOrderType.STOP_LIMIT,
                volume = 0.1,
                lowerLimitPrice = 101.0,
                lowerPrice = 100.0)))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("EURUSD", 100, listOf(
                IncomingLimitOrder(-5.0, 1.3, "m1"),
                IncomingLimitOrder(5.0, 1.1, "m2")
        )))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", 100, listOf(
                IncomingLimitOrder(-1.0, 8500.0, "m3")
        )))

        assertOrderBookSize("BTCUSD", false, 3)
        assertOrderBookSize("BTCUSD", true, 1)
        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 2)
        assertStopOrderBookSize("BTCUSD", true, 1)
        assertStopOrderBookSize("BTCUSD", false, 0)
        clearMessageQueues()
    }

    @Test
    fun testCancelOrdersOneSide() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper(1, "BTCUSD", false))

        assertOrderBookSize("BTCUSD", false, 1)
        assertOrderBookSize("BTCUSD", true, 1)
        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 2)
        assertStopOrderBookSize("BTCUSD", true, 1)
        assertStopOrderBookSize("BTCUSD", false, 0)

        assertBalance(1, "BTC", 1.0, 0.0)
        assertBalance(1, "USD", 100.0, 91.0)

        assertEquals(1, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(2, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "2" }.status)
        assertEquals(1, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "BTC", "1", "1", "0.6", "0", event.balanceUpdates!!)
    }

    @Test
    fun cancelAllClientOrders() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper(1))

        assertOrderBookSize("BTCUSD", false, 1)
        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 1)
        assertStopOrderBookSize("BTCUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", false, 0)

        assertBalance(1, "BTC", 1.0, 0.0)
        assertBalance(1, "USD", 100.0, 0.0)
        assertEquals(3, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(5, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "2" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "3" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "4" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "5" }.status)
        assertEquals(2, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "BTC", "1", "1", "0.6", "0", event.balanceUpdates!!)
        assertEventBalanceUpdate(1, "USD", "100", "100", "91", "0", event.balanceUpdates!!)
    }

    @Test
    fun testCancelTrustedClientOrders() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper(100, "EURUSD"))

        assertOrderBookSize("BTCUSD", false, 3)
        assertOrderBookSize("BTCUSD", true, 1)
        assertOrderBookSize("EURUSD", false, 0)
        assertOrderBookSize("EURUSD", true, 1)

        assertEquals(2, outgoingOrderBookQueue.size)

        assertEquals(0, clientsEventsQueue.size)
        assertEquals(1, trustedClientsEventsQueue.size)
        val event = trustedClientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(0, event.balanceUpdates?.size)
        assertEquals(2, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m2" }.status)
    }

    @Test
    fun testCancelBuyOrdersByAssetPair() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper(assetPairId = "BTCUSD", isBuy = true))

        assertOrderBookSize("BTCUSD", false, 3)
        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 2)
        assertStopOrderBookSize("BTCUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", false, 0)

        assertEquals(1, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(2, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "3" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "5" }.status)
        assertEquals(1, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "USD", "100", "100", "91", "11", event.balanceUpdates!!)


        assertEquals(0, trustedClientsEventsQueue.size)
    }

    @Test
    fun testCancelSellOrdersByAssetPair() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper(assetPairId = "BTCUSD", isBuy = false))

        assertOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 1)
        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 2)
        assertStopOrderBookSize("BTCUSD", true, 1)
        assertStopOrderBookSize("BTCUSD", false, 0)

        assertEquals(1, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        var event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(2, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "2" }.status)
        assertEquals(1, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "BTC", "1", "1", "0.6", "0", event.balanceUpdates!!)

        assertEquals(1, trustedClientsEventsQueue.size)
        event = trustedClientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(0, event.balanceUpdates?.size)
        assertEquals(1, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m3" }.status)
    }

    @Test
    fun testCancelOrdersByAssetPair() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper(assetPairId = "BTCUSD"))

        assertOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 2)
        assertStopOrderBookSize("BTCUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", false, 0)

        assertEquals(2, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        var event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(4, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "2" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "3" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "5" }.status)
        assertEquals(2, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "BTC", "1", "1", "0.6", "0", event.balanceUpdates!!)
        assertEventBalanceUpdate(1, "USD", "100", "100", "91", "11", event.balanceUpdates!!)


        assertEquals(1, trustedClientsEventsQueue.size)
        event = trustedClientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(0, event.balanceUpdates?.size)
        assertEquals(1, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m3" }.status)
    }

    @Test
    fun testCancelAllBuyOrders() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper(isBuy = true))

        assertOrderBookSize("BTCUSD", false, 3)
        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("EURUSD", false, 1)
        assertOrderBookSize("EURUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", false, 0)

        assertEquals(2, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        var event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(3, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "3" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "4" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "5" }.status)
        assertEquals(1, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "USD", "100", "100", "91", "0", event.balanceUpdates!!)


        assertEquals(1, trustedClientsEventsQueue.size)
        event = trustedClientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(0, event.balanceUpdates?.size)
        assertEquals(1, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m2" }.status)
    }

    @Test
    fun testCancelAllSellOrders() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper(isBuy = false))

        assertOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 1)
        assertOrderBookSize("EURUSD", false, 0)
        assertOrderBookSize("EURUSD", true, 2)
        assertStopOrderBookSize("BTCUSD", true, 1)
        assertStopOrderBookSize("BTCUSD", false, 0)

        assertEquals(2, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        var event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(2, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "2" }.status)
        assertEquals(1, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "BTC", "1", "1", "0.6", "0", event.balanceUpdates!!)

        assertEquals(1, trustedClientsEventsQueue.size)
        event = trustedClientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(0, event.balanceUpdates?.size)
        assertEquals(2, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m3" }.status)
    }

    @Test
    fun testCancelAllOrders() {
        setOrders()

        limitOrderMassCancelService.processMessage(messageBuilder.buildLimitOrderMassCancelWrapper())

        assertOrderBookSize("BTCUSD", false, 0)
        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("EURUSD", false, 0)
        assertOrderBookSize("EURUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", true, 0)
        assertStopOrderBookSize("BTCUSD", false, 0)

        assertEquals(4, outgoingOrderBookQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        var event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(5, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "2" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "3" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "4" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "5" }.status)
        assertEquals(2, event.balanceUpdates?.size)
        assertEventBalanceUpdate(1, "BTC", "1", "1", "0.6", "0", event.balanceUpdates!!)
        assertEventBalanceUpdate(1, "USD", "100", "100", "91", "0", event.balanceUpdates!!)


        assertEquals(1, trustedClientsEventsQueue.size)
        event = trustedClientsEventsQueue.poll() as ExecutionEvent
        assertEquals(MessageType.LIMIT_ORDER_MASS_CANCEL.name, event.header.eventType)
        assertEquals(0, event.balanceUpdates?.size)
        assertEquals(3, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m1" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m2" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "m3" }.status)
    }
}