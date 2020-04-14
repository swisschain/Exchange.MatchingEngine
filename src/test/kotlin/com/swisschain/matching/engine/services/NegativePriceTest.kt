package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.IncomingLimitOrder
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus
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

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (NegativePriceTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NegativePriceTest : AbstractTest() {

    @Autowired
    private lateinit var testSettingsDatabaseAccessor: TestSettingsDatabaseAccessor

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
        testBalanceHolderWrapper.updateBalance("Client", "USD", 1.0)
        testBalanceHolderWrapper.updateReservedBalance("Client", "USD", 0.0)

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))

        initServices()
    }

    @Test
    fun testLimitOrder() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client", assetId = "EURUSD", price = -1.0, volume = 1.0)))

        assertEquals(1, clientsEventsQueue.size)
        val result = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(1, result.orders.size)
        assertEquals(OrderStatus.REJECTED, result.orders.first().status)
        assertEquals(OrderRejectReason.INVALID_PRICE, result.orders.first().rejectReason)
    }

    @Test
    fun testTrustedClientMultiLimitOrder() {
        testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("Client"))

        initServices()

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("EURUSD",
                "Client",
                listOf(
                        IncomingLimitOrder(1.0, 1.0, uid = "order1"),
                        IncomingLimitOrder(1.0, -1.0, uid = "order2")
                )))

        assertEquals(1, trustedClientsEventsQueue.size)
        val result = trustedClientsEventsQueue.poll()
        assertEquals(1, result.orders.size)
        assertEquals(OrderStatus.PLACED, result.orders.first { it.externalId == "order1" }.status)

        assertEquals(1, testOrderDatabaseAccessor.getOrders("EURUSD", true).size)
    }
}