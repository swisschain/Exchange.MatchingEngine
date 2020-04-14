package com.swisschain.matching.engine.utils.order

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
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMultiLimitOrderWrapper
import com.swisschain.matching.engine.utils.assertEquals
import com.swisschain.matching.engine.utils.balance.ReservedVolumesRecalculator
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (MinVolumeOrderCancellerTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MinVolumeOrderCancellerTest : AbstractTest() {

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @TestConfiguration
    class Config {

        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
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
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.TRUSTED_CLIENTS, getSetting("TrustedClient"))
            return testSettingsDatabaseAccessor
        }

    }

    @Autowired
    private lateinit var recalculator: ReservedVolumesRecalculator

    @Before
    fun setUp() {
        testBalanceHolderWrapper.updateBalance("Client1", "BTC", 1.0)
        testBalanceHolderWrapper.updateBalance("TrustedClient", "BTC", 2.0)
        testBalanceHolderWrapper.updateBalance("Client2", "BTC", 3.0)
        testBalanceHolderWrapper.updateBalance("ClientForPartiallyMatching", "BTC", 3.0)

        testBalanceHolderWrapper.updateBalance("Client1", "EUR", 100.0)
        testBalanceHolderWrapper.updateBalance("TrustedClient", "EUR", 200.0)
        testBalanceHolderWrapper.updateBalance("Client2", "EUR", 300.0)
        testBalanceHolderWrapper.updateBalance("ClientForPartiallyMatching", "EUR", 300.0)

        testBalanceHolderWrapper.updateBalance("Client1", "USD", 1000.0)
        testBalanceHolderWrapper.updateBalance("TrustedClient", "USD", 2000.0)
        testBalanceHolderWrapper.updateBalance("Client2", "USD", 3000.0)
        testBalanceHolderWrapper.updateBalance("ClientForPartiallyMatching", "USD", 3000.0)

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "EUR", 5))

        initServices()
    }

    @Test
    fun testCancel() {
        // BTCEUR
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCEUR", price = 9000.0, volume = -1.0)))

        // BTCUSD
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "BTCUSD", price = 10000.0, volume = 0.00001)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "validVolume", walletId = "Client1", assetId = "BTCUSD", price = 10001.0, volume = 0.01)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client2", assetId = "BTCUSD", price = 10001.0, volume = 0.001)))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper(walletId = "TrustedClient", pair = "BTCUSD",
                orders = listOf(IncomingLimitOrder(0.00102, 10002.0), IncomingLimitOrder(-0.00001, 11000.0))))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "ClientForPartiallyMatching", assetId = "BTCUSD", price = 10002.0, volume = -0.001)))


        // EURUSD
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "EURUSD", price = 1.2, volume = -10.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "Client1", assetId = "EURUSD", price = 1.1, volume = 10.0)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = "Client2", assetId = "EURUSD", price = 1.3, volume = -4.09)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order2", walletId = "Client2", assetId = "EURUSD", price = 1.1, volume = 4.09)))

        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper(walletId = "TrustedClient", pair = "EURUSD",
                orders = listOf(IncomingLimitOrder(30.0, 1.1), IncomingLimitOrder(-30.0, 1.4))))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = "ClientForPartiallyMatching", assetId = "EURUSD", price = 1.2, volume = 6.0)))


        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5, BigDecimal.valueOf(0.0001)))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2,  BigDecimal.valueOf(5.0)))
        initServices()

        minVolumeOrderCanceller.cancel()

        assertEquals(BigDecimal.valueOf (2.001), testWalletDatabaseAccessor.getBalance("TrustedClient", "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance("TrustedClient", "BTC"))

        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance("TrustedClient", "USD"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance("TrustedClient", "EUR"))

        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getBalance("Client1", "BTC"))
        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getReservedBalance("Client1", "BTC"))

        assertEquals(BigDecimal.valueOf(1007.2), testWalletDatabaseAccessor.getBalance("Client1", "USD"))
        assertEquals(BigDecimal.valueOf(111.01), testWalletDatabaseAccessor.getReservedBalance("Client1", "USD"))

        assertEquals(BigDecimal.valueOf(94.0), testWalletDatabaseAccessor.getBalance("Client1", "EUR"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance("Client1", "EUR"))

        assertEquals(BigDecimal.valueOf(3000.0), testWalletDatabaseAccessor.getBalance("Client2", "USD"))
        assertEquals(BigDecimal.valueOf(10.01), testWalletDatabaseAccessor.getReservedBalance("Client2", "USD"))

        assertEquals(BigDecimal.valueOf(300.0), testWalletDatabaseAccessor.getBalance("Client2", "EUR"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance("Client2", "EUR"))

        // BTCUSD
        assertEquals(1, testOrderDatabaseAccessor.getOrders("BTCUSD", true).filter { it.walletId == "Client1" }.size)
        // check order is removed from clientOrdersMap
        assertEquals(1, genericLimitOrderService.searchOrders(DEFAULT_BROKER, "Client1", "BTCUSD", true).size)

        assertEquals("validVolume", testOrderDatabaseAccessor.getOrders("BTCUSD", true).first { it.walletId == "Client1" }.externalId)

        assertFalse(testOrderDatabaseAccessor.getOrders("BTCUSD", true).any { it.walletId == "TrustedClient" })
        assertFalse(testOrderDatabaseAccessor.getOrders("BTCUSD", false).any { it.walletId == "TrustedClient" })

        assertEquals(1, testOrderDatabaseAccessor.getOrders("BTCUSD", true).filter { it.walletId == "Client2" }.size)
        // check order is removed from clientOrdersMap
        assertEquals(1, genericLimitOrderService.searchOrders(DEFAULT_BROKER, "Client2", "BTCUSD", true).size)

        // EURUSD
        assertEquals(1, testOrderDatabaseAccessor.getOrders("EURUSD", true).filter { it.walletId == "Client1" }.size)
        assertFalse(testOrderDatabaseAccessor.getOrders("EURUSD", false).any { it.walletId == "Client1" })

        assertEquals(1, testOrderDatabaseAccessor.getOrders("EURUSD", true).filter { it.walletId == "TrustedClient" }.size)
        assertEquals(1, testOrderDatabaseAccessor.getOrders("EURUSD", false).filter { it.walletId == "TrustedClient" }.size)

        assertFalse(testOrderDatabaseAccessor.getOrders("EURUSD", true).any { it.walletId == "Client2" })
        assertFalse(testOrderDatabaseAccessor.getOrders("EURUSD", false).any { it.walletId == "Client2" })

        // check order is removed from ordersMap
        assertNull(genericLimitOrderService.cancelLimitOrder(DEFAULT_BROKER, Date(), "order1", false))
        assertNull(genericLimitOrderService.cancelLimitOrder(DEFAULT_BROKER, Date(), "order2", false))

        assertEquals(1, trustedClientsEventsQueue.size)
        assertEquals(1, (trustedClientsEventsQueue.first() as ExecutionEvent).orders.size)
        assertEquals("11000", (trustedClientsEventsQueue.first() as ExecutionEvent).orders.first().price)

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(5, (clientsEventsQueue.first() as ExecutionEvent).orders.size)

        assertEquals(4, outgoingOrderBookQueue.size)
    }

    @Test
    fun testCancelOrdersWithRemovedAssetPair() {
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = "Client1", assetId = "BTCEUR", price = 10000.0, volume = -1.0)))
        multiLimitOrderService.processMessage(buildMultiLimitOrderWrapper("BTCEUR", "TrustedClient",
                listOf(IncomingLimitOrder(-1.0, price = 10000.0, uid = "order2"))))

        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, "TrustedClient", "BTC"))
        assertEquals(BigDecimal.valueOf( 1.0), balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(2, testOrderDatabaseAccessor.getOrders("BTCEUR", false).size)
        assertEquals(2, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCEUR").getOrderBook(false).size)

        testDictionariesDatabaseAccessor.clear() // remove asset pair BTCEUR
        initServices()
        minVolumeOrderCanceller.cancel()

        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCEUR", false).size)
        assertEquals(0, genericLimitOrderService.getOrderBook(DEFAULT_BROKER, "BTCEUR").getOrderBook(false).size)

        // check order is removed from ordersMap
        assertNull(genericLimitOrderService.cancelLimitOrder(DEFAULT_BROKER, Date(), "order1", false))
        assertNull(genericLimitOrderService.cancelLimitOrder(DEFAULT_BROKER, Date(), "order2", false))

        // check order is removed from clientOrdersMap
        assertEquals(0, genericLimitOrderService.searchOrders(DEFAULT_BROKER, "Client1", "BTCEUR", false).size)
        assertEquals(0, genericLimitOrderService.searchOrders(DEFAULT_BROKER, "TrustedClient", "BTCEUR", false).size)

        assertEquals(BigDecimal.valueOf(1.0), testWalletDatabaseAccessor.getReservedBalance("Client1", "BTC"))
        assertEquals(BigDecimal.valueOf(1.0), balancesHolder.getReservedBalance(DEFAULT_BROKER, "Client1", "BTC"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance("TrustedClient", "BTC"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getReservedBalance(DEFAULT_BROKER, "TrustedClient", "BTC"))

        // recalculate reserved volumes to reset locked reservedAmount
        recalculator.recalculate()
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance("Client1", "BTC"))
    }

}