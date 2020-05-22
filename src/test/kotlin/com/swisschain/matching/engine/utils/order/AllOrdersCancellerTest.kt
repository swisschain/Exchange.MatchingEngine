package com.swisschain.matching.engine.utils.order

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
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
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (AllOrdersCancellerTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AllOrdersCancellerTest: AbstractTest() {

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()

            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("LKK1Y", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("LKK", 2))
            testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))
            testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "EUR", 8))

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

    @Autowired
    private lateinit var allOrdersCanceller: AllOrdersCanceller

    @Autowired
    private lateinit var reservedVolumesRecalculator: ReservedVolumesRecalculator

    @Before
    fun init() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 10000.0)
        testBalanceHolderWrapper.updateBalance(2, "BTC", 0.5)

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "EUR", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("LKK1YLKK", "LKK1Y", "LKK", 5))

        initServices()
    }

    @Test
    fun testCancelAllOrders() {
        //given
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCUSD", price = 3500.0, volume = 0.5)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCUSD", price = 6500.0, volume = 0.5)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 2, assetId = "BTCUSD", price = 6000.0, volume = -0.25)))
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = 1, assetId = "EURUSD", volume = 10.0,
                type = LimitOrderType.STOP_LIMIT, lowerLimitPrice = 10.0, lowerPrice = 10.5
        )))

        clearMessageQueues()
        //when
        allOrdersCanceller.cancelAllOrders()

        //then
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(1, "USD"))
        assertEquals(BigDecimal.ZERO, testWalletDatabaseAccessor.getReservedBalance(2, "BTC"))

        assertEquals(BigDecimal.valueOf(0.25), testWalletDatabaseAccessor.getBalance(1, "BTC"))
        assertEquals(BigDecimal.valueOf(8375.0), testWalletDatabaseAccessor.getBalance(1, "USD"))
        assertEquals(BigDecimal.valueOf(1625.0), testWalletDatabaseAccessor.getBalance(2, "USD"))
        assertEquals(BigDecimal.valueOf(0.25), testWalletDatabaseAccessor.getBalance(2, "BTC"))

        assertEquals(0, testOrderDatabaseAccessor.getOrders("EURUSD", true).size)
        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCUSD", true).size)
        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCUSD", false).size)

        assertEquals(1, clientsEventsQueue.size)
        assertEquals(3,  (clientsEventsQueue.poll() as ExecutionEvent).orders.size)
    }

    @Test
    fun testCancelAllOrdersWithRemovedAssetPairs() {
        //given
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(uid = "order1", walletId = 1, assetId = "BTCUSD", price = 6000.0, volume = 1.0)))

        testDictionariesDatabaseAccessor.clear()
        initServices()

        //when
        allOrdersCanceller.cancelAllOrders()

        //then
        assertEquals(BigDecimal.valueOf(6000), testWalletDatabaseAccessor.getReservedBalance(1, "USD"))
        assertEquals(BigDecimal.valueOf(10000), testWalletDatabaseAccessor.getBalance(1, "USD"))
        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCEUR", false).size)
    }

    @Test
    fun testCancelAllOrdersWithEmptyReservedLimitVolume() {
        testBalanceHolderWrapper.updateBalance(1, "LKK", 1.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "LKK1YLKK", volume = 5.0, price = 0.021))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, assetId = "LKK1YLKK", volume = 5.0, price = 0.021))

        reservedVolumesRecalculator.recalculate()

        allOrdersCanceller.cancelAllOrders()

        assertOrderBookSize("LKK1YLKK", true, 0)
        assertBalance(1, "LKK", 1.0, 0.0)
        assertBalance(1, "LKK1Y", 0.0, 0.0)
    }

}