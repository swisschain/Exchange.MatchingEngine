package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrderWrapper
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
import java.math.BigDecimal
import kotlin.test.assertEquals
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (MinRemainingVolumeTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MinRemainingVolumeTest : AbstractTest() {


    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testConfig(): TestSettingsDatabaseAccessor {
            val testSettingsDatabaseAccessor = TestSettingsDatabaseAccessor()
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS,  getSetting("Client3"))
            return testSettingsDatabaseAccessor
        }

        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()

            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))

            return testDictionariesDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Before
    fun setUp() {
        testBalanceHolderWrapper.updateBalance("Client1", "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance("Client1", "USD", 10000.0)

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8, BigDecimal.valueOf(0.01)))
        initServices()
    }

    @Test
    fun testIncomingLimitOrder() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = -0.1, price = 8000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = -0.1, price = 8100.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = -0.1, price = 8200.0)))

        testBalanceHolderWrapper.updateBalance("Client2", "USD", 1800.0)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", volume = 0.1991, price = 9000.0)))

        assertOrderBookSize("BTCUSD", false, 1)
        assertBalance("Client1", "BTC", reserved = 0.1)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, event.orders.size)
        assertEquals(1, event.orders.filter { it.externalId == "order1" }.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "order1" }.status)
    }

    @Test
    fun testIncomingLimitOrderWithMinRemaining() {
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.3)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = 0.1, price = 7000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = 0.1, price = 6900.0)))

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = "Client2", assetId = "BTCUSD", volume = -0.2009, price = 6900.0)))

        assertOrderBookSize("BTCUSD", false, 0)
        assertBalance("Client2", "BTC", 0.1, 0.0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, event.orders.size)
        assertEquals(1, event.orders.filter { it.externalId == "order1" }.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "order1" }.status)
    }

    @Test
    fun testIncomingMarketOrder() {
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 0.2)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = 0.1, price = 7000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = 0.1009, price = 6900.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = 0.1, price = 6800.0)))

        clearMessageQueues()
        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(walletId = "Client2", assetId = "BTCUSD", volume = -0.2)))

        assertOrderBookSize("BTCUSD", true, 1)
        assertBalance("Client1", "USD", reserved = 680.0)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, event.orders.size)
        assertEquals(1, event.orders.filter { it.externalId == "order1" }.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "order1" }.status)
    }

    @Test
    fun testIncomingMultiLimitOrder() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = -0.1, price = 8000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = "Client1", assetId = "BTCUSD", volume = -0.1, price = 8100.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = -0.1, price = 8200.0)))

        testBalanceHolderWrapper.updateBalance("TrustedClient", "USD", 1800.0)
        initServices()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", "TrustedClient", listOf(
                IncomingLimitOrder(0.11, 9000.0),
                IncomingLimitOrder(0.0891, 8900.0))))

        assertOrderBookSize("BTCUSD", false, 1)
        assertBalance("Client1", "BTC", reserved = 0.1)

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.filter { it.externalId == "order1" }.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "order1" }.status)
    }

    @Test
    fun testIncomingMultiLimitOrderWithMinRemaining() {
        testBalanceHolderWrapper.updateBalance("TrustedClient", "BTC", 0.3)
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = 0.1, price = 7000.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", volume = 0.1, price = 6900.0)))

        clearMessageQueues()
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCUSD", "TrustedClient", listOf(
                IncomingLimitOrder(-0.11, 6800.0, "order1"),
                IncomingLimitOrder(-0.0909, 6900.0, "order2"))))

        assertOrderBookSize("BTCUSD", false, 0)

        assertBalance("TrustedClient", "BTC", 0.1)
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, event.orders.filter { it.externalId == "order2" }.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "order2" }.status)
    }
}