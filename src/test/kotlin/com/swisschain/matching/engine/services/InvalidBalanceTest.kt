package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrderWrapper
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMultiLimitOrderWrapper
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
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (InvalidBalanceTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InvalidBalanceTest : AbstractTest() {

    @Autowired
    private
    lateinit var testSettingDatabaseAccessor: TestSettingsDatabaseAccessor

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @TestConfiguration
    class Config {

        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("ETH", 8))

            return testDictionariesDatabaseAccessor
        }
    }

    @Before
    fun setUp() {
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("ETHUSD", "ETH", "USD", 5))
        initServices()
    }

    @Test
    fun testLimitOrderLeadsToInvalidBalance() {

        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 0.02)
        testBalanceHolderWrapper.updateReservedBalance(walletId = 1, assetId = "USD", reservedBalance = 0.0)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "ETH", balance = 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(walletId = 2, assetId = "ETH", reservedBalance = 0.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "ETHUSD", volume = -0.000005, price = 1000.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "ETHUSD", volume = -0.000005, price = 1000.0))

        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "ETHUSD", price = 1000.0, volume = 0.00002)))

        assertEquals(0, trustedClientsEventsQueue.size)
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.size)
        assertEquals(OutgoingOrderStatus.REJECTED, event.orders.single().status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, event.orders.single().rejectReason)

        assertEquals(0, outgoingOrderBookQueue.size)
        assertEquals(0, outgoingOrderBookQueue.size)

        assertEquals(0, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "ETHUSD").getOrderBook(true).size)
        assertEquals(0, testOrderDatabaseAccessor.getOrders("ETHUSD", true).size)
        assertEquals(2, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "ETHUSD").getOrderBook(false).size)
        assertEquals(2, testOrderDatabaseAccessor.getOrders("ETHUSD", false).size)
        genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "ETHUSD").getOrderBook(false).forEach {
            assertEquals(2, it.walletId)
            assertEquals(BigDecimal.valueOf(-0.000005), it.remainingVolume)
            assertEquals(OrderStatus.InOrderBook.name, it.status)
        }

        assertBalance(1, "USD", 0.02, 0.0)
        assertEquals(BigDecimal.ZERO, balancesHolder.getBalance(DEFAULT_BROKER, 1, "ETH"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getBalance(1, "ETH"))

        assertBalance(2, "ETH", 1000.0, 0.0)
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, 2, "USD"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(2, "USD"))
    }

    @Test
    fun testMarketOrderWithPreviousInvalidBalance() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 0.02)
        testBalanceHolderWrapper.updateReservedBalance(walletId = 1, assetId = "USD", reservedBalance = 0.0)

        // invalid opposite wallet
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "ETH", balance = 1.0)
        testBalanceHolderWrapper.updateReservedBalance(walletId = 2, assetId = "ETH", reservedBalance = 1.1)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "ETHUSD", volume = -0.000005, price = 1000.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "ETHUSD", volume = -0.000005, price = 1000.0))

        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = 1, assetId = "ETHUSD", volume = 0.00001)))

        assertEquals(0, trustedClientsEventsQueue.size)
        assertEquals(1, clientsEventsQueue.size)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, event.orders.size)
        event.orders.forEach {
            assertEquals(OutgoingOrderStatus.MATCHED, it.status)
        }

        assertEquals(1, outgoingOrderBookQueue.size)
        assertEquals(1, outgoingOrderBookQueue.size)

        assertEquals(0, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "ETHUSD").getOrderBook(true).size)
        assertEquals(0, testOrderDatabaseAccessor.getOrders("ETHUSD", true).size)
        assertEquals(0, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "ETHUSD").getOrderBook(false).size)
        assertEquals(0, testOrderDatabaseAccessor.getOrders("ETHUSD", false).size)

        assertBalance(1, "USD", 0.0, 0.0)
        assertEquals(BigDecimal.valueOf(0.00001), balancesHolder.getBalance(DEFAULT_BROKER, 1, "ETH"))
        assertEquals(BigDecimal.valueOf(0.00001), testWalletDatabaseAccessor.getBalance(1, "ETH"))

        assertBalance(2, "ETH", 0.99999, 1.09999)
        assertEquals(BigDecimal.valueOf(0.02), balancesHolder.getBalance(DEFAULT_BROKER, 2, "USD"))
        assertEquals(BigDecimal.valueOf(0.02), testWalletDatabaseAccessor.getBalance(2, "USD"))
    }

    @Test
    fun testNegativeBalanceDueToTransferWithOverdraftLimit() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 3.0)
        testBalanceHolderWrapper.updateBalance(1, "ETH", 3.0)

        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "ETHUSD", price = 1.0, volume = 3.0)))

        assertBalance(1, "USD", 3.0, 3.0)

        cashTransferOperationsService.processMessage(messageBuilder.buildTransferWrapper(1, 2, "USD", 4.0, 4.0))

        assertBalance(1, "USD", -1.0, 3.0)

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "ETHUSD", price = 1.1, volume = -0.5)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 2, assetId = "ETHUSD", price = 1.1, volume = 0.5)))

        assertBalance(1, "USD", -0.45, 3.0)

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 2, assetId = "ETHUSD", price = 1.0, volume = -0.5)))

        assertBalance(1, "USD", -0.45, 0.0)

        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.first { it.walletId == 1L }.status)

    }

    @Test
    fun testMultiLimitOrderWithNotEnoughReservedFunds() {
        testBalanceHolderWrapper.updateBalance(1, "ETH", 0.25)
        testBalanceHolderWrapper.updateBalance(2, "USD", 275.0)

        initServices()

        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.TRUSTED_CLIENTS, "1", "1", true)

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("ETHUSD", 1, listOf(
                IncomingLimitOrder(-0.1, 1000.0, "1"),
                IncomingLimitOrder(-0.05, 1010.0, "2"),
                IncomingLimitOrder(-0.1, 1100.0, "3")
        )))
        testBalanceHolderWrapper.updateReservedBalance(1, "ETH", reservedBalance = 0.09)
        testSettingDatabaseAccessor.clear()
        applicationSettingsCache.update()
        applicationSettingsHolder.update()

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "4", walletId = 2, assetId = "ETHUSD", volume = 0.25, price = 1100.0)))

        assertEquals(1, testOrderDatabaseAccessor.getOrders("ETHUSD", true).size)
        assertEquals(BigDecimal.valueOf(0.2), balancesHolder.getBalance(DEFAULT_BROKER, 1, "ETH"))
        assertEquals(BigDecimal.valueOf(0.04), balancesHolder.getReservedBalance(DEFAULT_BROKER, 1, "ETH"))
        assertEquals(BigDecimal.valueOf(0.05), balancesHolder.getBalance(DEFAULT_BROKER, 2, "ETH"))
        assertEquals(BigDecimal.valueOf(224.5), balancesHolder.getBalance(DEFAULT_BROKER, 2, "USD"))

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(4, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "1" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "2" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "3" }.status)
        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, event.orders.single { it.externalId == "4" }.status)
    }

    @Test
    fun `Test multi limit order with enough reserved but not enough main balance`() {
        testBalanceHolderWrapper.updateBalance(1, "ETH", 0.1)
        testBalanceHolderWrapper.updateReservedBalance(1, "ETH", reservedBalance = 0.05)
        testBalanceHolderWrapper.updateBalance(2, "USD", 275.0)

        initServices()
        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.TRUSTED_CLIENTS, "1", "1", true)

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("ETHUSD", 1,
                listOf(IncomingLimitOrder(-0.05, 1010.0, "1"))))
        testBalanceHolderWrapper.updateBalance(1, "ETH", 0.04)
        testSettingDatabaseAccessor.clear()
        applicationSettingsCache.update()
        applicationSettingsHolder.update()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "2", walletId = 2, assetId = "ETHUSD", volume = 0.25, price = 1100.0)))

        assertEquals(0, testOrderDatabaseAccessor.getOrders("ETHUSD", false).size)
        assertEquals(1, testOrderDatabaseAccessor.getOrders("ETHUSD", true).size)
        assertEquals(BigDecimal.valueOf(0.04), balancesHolder.getBalance(DEFAULT_BROKER, 1, "ETH"))
        assertEquals(BigDecimal.valueOf(275.0), balancesHolder.getReservedBalance(DEFAULT_BROKER, 2, "USD"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, 1, "ETH"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getBalance(DEFAULT_BROKER, 2, "ETH"))
        assertEquals(BigDecimal.valueOf(275.0), balancesHolder.getBalance(DEFAULT_BROKER, 2, "USD"))

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.orders.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "1" }.status)
        assertEquals(OutgoingOrderStatus.PLACED, event.orders.single { it.externalId == "2" }.status)
    }
}