package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeSizeType
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.fee.v2.NewLimitOrderFeeInstruction
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrderWrapper
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
import kotlin.test.assertNull
import com.swisschain.matching.engine.outgoing.messages.v2.enums.OrderStatus as OutgoingOrderStatus

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (FeeTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FeeTest: AbstractTest() {

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @TestConfiguration
    class Config {

        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))

            return testDictionariesDatabaseAccessor
        }
    }

    @Before
    fun setUp() {
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))

        initServices()
    }

    @Test
    fun testBuyLimitOrderFeeOppositeAsset() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "BTC", balance = 0.1)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "USD", balance = 100.0)
        testBalanceHolderWrapper.updateBalance(walletId = 4, assetId = "USD", balance = 10.0)
        testBalanceHolderWrapper.updateBalance(walletId = 4, assetId = "BTC", balance = 0.1)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(
                walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = -0.05,
                fees = listOf(
                        buildLimitOrderFeeInstruction(
                                type = FeeType.CLIENT_FEE,
                                makerSizeType = FeeSizeType.PERCENTAGE,
                                makerSize = BigDecimal.valueOf(0.04),
                                targetWalletId = 3,
                                assetIds = listOf("BTC"))!!,
                        buildLimitOrderFeeInstruction(
                                type = FeeType.EXTERNAL_FEE,
                                makerSizeType = FeeSizeType.PERCENTAGE,
                                makerSize = BigDecimal.valueOf(0.05),
                                sourceWalletId = 4,
                                targetWalletId = 3,
                                assetIds = listOf("BTC"))!!
                )
        ))
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = 0.005,
                fees = listOf(
                        buildLimitOrderFeeInstruction(
                                type = FeeType.CLIENT_FEE,
                                takerSizeType = FeeSizeType.PERCENTAGE,
                                takerSize = BigDecimal.valueOf(0.03),
                                targetWalletId = 3,
                                assetIds = listOf("USD"))!!,
                        buildLimitOrderFeeInstruction(
                                type = FeeType.EXTERNAL_FEE,
                                takerSizeType = FeeSizeType.PERCENTAGE,
                                takerSize = BigDecimal.valueOf(0.02),
                                sourceWalletId = 4,
                                targetWalletId = 3,
                                assetIds = listOf("USD"))!!
                )
        )))

        assertEquals(BigDecimal.valueOf(75.0), balancesHolder.getBalance(DEFAULT_BROKER, 1, "USD"))
        assertEquals(BigDecimal.valueOf(0.0948), balancesHolder.getBalance(DEFAULT_BROKER, 1, "BTC"))
        assertEquals(BigDecimal.valueOf(0.00045), balancesHolder.getBalance(DEFAULT_BROKER, 3, "BTC"))
        assertEquals(BigDecimal.valueOf(3.75), balancesHolder.getBalance(DEFAULT_BROKER, 3, "USD"))
        assertEquals(BigDecimal.valueOf(22.75), balancesHolder.getBalance(DEFAULT_BROKER, 2, "USD"))
        assertEquals(BigDecimal.valueOf(0.005), balancesHolder.getBalance(DEFAULT_BROKER, 2, "BTC"))
        assertEquals(BigDecimal.valueOf(0.09975), balancesHolder.getBalance(DEFAULT_BROKER, 4, "BTC"))
        assertEquals(BigDecimal.valueOf(8.5), balancesHolder.getBalance(DEFAULT_BROKER, 4, "USD"))

        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.orders.size)
        val taker = event.orders.single { it.walletId == 2L }
        assertEquals(1, taker.trades?.size)
        assertEquals(2, taker.fees?.size)
        val takerTrade = taker.trades!!.first()
        assertEquals(2, takerTrade.fees?.size)

        val feeInstruction1 = taker.fees!!.single { it.size == "0.03" }
        val feeTransfer1 = takerTrade.fees!!.single { it.index == feeInstruction1.index }
        assertEquals("2.25", feeTransfer1.volume)
        assertEquals("USD", feeTransfer1.assetId)
        assertEquals(3, feeTransfer1.targetWalletId)
        val feeInstruction2 = taker.fees!!.single { it.size == "0.02" }
        val feeTransfer2 = takerTrade.fees!!.single { it.index == feeInstruction2.index }
        assertEquals("1.5", feeTransfer2.volume)
        assertEquals("USD", feeTransfer2.assetId)
        assertEquals(3, feeTransfer2.targetWalletId)
    }

    @Test
    fun testBuyLimitOrderFeeAnotherAsset() {
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "EUR", 8))

        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "BTC", balance = 0.1)
        testBalanceHolderWrapper.updateReservedBalance(walletId = 1, assetId = "BTC", reservedBalance =  0.05)
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "EUR", balance = 25.0)

        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "USD", balance = 100.0)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "EUR", balance = 1.88)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "EURUSD", price = 1.3, volume = -1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "EURUSD", price = 1.1, volume = 1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "BTCEUR", price = 13000.0, volume = -1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "BTCEUR", price = 12000.0, volume = 1.0))


        testOrderBookWrapper.addLimitOrder(buildLimitOrder(
                walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = -0.05,
                fees = listOf(
                        buildLimitOrderFeeInstruction(
                                type = FeeType.CLIENT_FEE,
                                makerSizeType = FeeSizeType.PERCENTAGE,
                                makerSize = BigDecimal.valueOf(0.04),
                                targetWalletId = 3,
                                assetIds = listOf("EUR"))!!
                )
        ))
        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = 0.005,
                fees = listOf(
                        buildLimitOrderFeeInstruction(
                                type = FeeType.CLIENT_FEE,
                                takerSizeType = FeeSizeType.PERCENTAGE,
                                takerSize = BigDecimal.valueOf(0.03),
                                targetWalletId = 3,
                                assetIds = listOf("EUR"))!!
                )
        )))

        assertEquals(BigDecimal.valueOf(75.0), balancesHolder.getBalance(DEFAULT_BROKER, 1, "USD"))
        assertEquals(BigDecimal.valueOf(0.095), balancesHolder.getBalance(DEFAULT_BROKER, 1, "BTC"))
        assertEquals(BigDecimal.valueOf(22.5), balancesHolder.getBalance(DEFAULT_BROKER, 1, "EUR"))

        assertEquals(BigDecimal.valueOf(4.38), balancesHolder.getBalance(DEFAULT_BROKER, 3, "EUR"))

        assertEquals(BigDecimal.valueOf(25.00), balancesHolder.getBalance(DEFAULT_BROKER, 2, "USD"))
        assertEquals(BigDecimal.valueOf(0.005), balancesHolder.getBalance(DEFAULT_BROKER, 2, "BTC"))
        assertEquals(BigDecimal.ZERO, balancesHolder.getBalance(DEFAULT_BROKER, 2, "EUR"))
    }

    @Test
    fun testSellMarketOrderFeeOppositeAsset() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 100.0)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "BTC", balance = 0.1)

        testBalanceHolderWrapper.updateBalance(walletId = 4, assetId = "USD", balance = 10.0)
        testBalanceHolderWrapper.updateBalance(walletId = 4, assetId = "BTC", balance = 0.1)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(
                walletId = 1, assetId = "BTCUSD", price = 15154.123, volume = 0.005412,
                fees = listOf(
                        buildLimitOrderFeeInstruction(
                                type = FeeType.CLIENT_FEE,
                                makerSizeType = FeeSizeType.PERCENTAGE,
                                makerSize = BigDecimal.valueOf(0.04),
                                targetWalletId = 3,
                                assetIds = listOf("USD"))!!,
                        buildLimitOrderFeeInstruction(
                                type = FeeType.EXTERNAL_FEE,
                                makerSizeType = FeeSizeType.PERCENTAGE,
                                makerSize = BigDecimal.valueOf(0.05),
                                sourceWalletId = 4,
                                targetWalletId = 3,
                                assetIds = listOf("USD"))!!
                )
        ))

        initServices()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(
                walletId = 2, assetId = "BTCUSD", volume = -0.005,
                fees = listOf(
                        buildLimitOrderFeeInstruction(
                                type = FeeType.CLIENT_FEE,
                                takerSizeType = FeeSizeType.PERCENTAGE,
                                takerSize = BigDecimal.valueOf(0.03),
                                targetWalletId = 3,
                                assetIds = listOf("BTC"))!!,
                        buildLimitOrderFeeInstruction(
                                type = FeeType.EXTERNAL_FEE,
                                takerSizeType = FeeSizeType.PERCENTAGE,
                                takerSize = BigDecimal.valueOf(0.02),
                                sourceWalletId = 4,
                                targetWalletId = 3,
                                assetIds = listOf("BTC"))!!
                )
        )))

        assertEquals(BigDecimal.valueOf(0.005), balancesHolder.getBalance(DEFAULT_BROKER, 1, "BTC"))
        assertEquals(BigDecimal.valueOf(21.19), balancesHolder.getBalance(DEFAULT_BROKER, 1, "USD"))
        assertEquals(BigDecimal.valueOf(75.77), balancesHolder.getBalance(DEFAULT_BROKER, 2, "USD"))
        assertEquals(BigDecimal.valueOf(0.09485), balancesHolder.getBalance(DEFAULT_BROKER, 2, "BTC"))
        assertEquals(BigDecimal.valueOf(6.83), balancesHolder.getBalance(DEFAULT_BROKER, 3, "USD"))
        assertEquals(BigDecimal.valueOf(0.00025), balancesHolder.getBalance(DEFAULT_BROKER, 3, "BTC"))
        assertEquals(BigDecimal.valueOf(0.0999), balancesHolder.getBalance(DEFAULT_BROKER, 4, "BTC"))
        assertEquals(BigDecimal.valueOf(6.21), balancesHolder.getBalance(DEFAULT_BROKER, 4, "USD"))
    }

    @Test
    fun testOrderBookNotEnoughFundsForFee() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 750.0)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "BTC", balance = 0.0503)

        initServices()

        for (i in 1..5) {
            singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                    uid = "order$i", walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.01,
                    fees = listOf(buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE,
                            makerSizeType = FeeSizeType.PERCENTAGE,
                            makerSize = BigDecimal.valueOf(0.01),
                            targetWalletId = 3,
                            assetIds = listOf("BTC"))!!))))
            Thread.sleep(10)
        }

        assertEquals(5, testOrderDatabaseAccessor.getOrders("BTCUSD", false).size)

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order", walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = 0.05
        )))

        assertEquals(OutgoingOrderStatus.PLACED, (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.externalId == "order1" }.status)
        assertEquals(OutgoingOrderStatus.PLACED, (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.externalId == "order2" }.status)
        assertEquals(OutgoingOrderStatus.PLACED, (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.externalId == "order3" }.status)
        assertEquals(OutgoingOrderStatus.PLACED, (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.externalId == "order4" }.status)
        assertEquals(OutgoingOrderStatus.PLACED, (clientsEventsQueue.poll() as ExecutionEvent).orders.first { it.externalId == "order5" }.status)

        val result = (clientsEventsQueue.poll() as ExecutionEvent).orders
        assertEquals(OutgoingOrderStatus.MATCHED, result.first { it.externalId == "order1" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, result.first { it.externalId == "order2" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, result.first { it.externalId == "order3" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, result.first { it.externalId == "order4" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, result.first { it.externalId == "order5" }.status)
        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, result.first { it.externalId == "order" }.status)

        assertEquals(BigDecimal.valueOf(0.02), balancesHolder.getBalance(DEFAULT_BROKER, 2, "BTC"))
        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCUSD", false).size)
        assertEquals(1, testOrderDatabaseAccessor.getOrders("BTCUSD", true).size)
    }

    @Test
    fun testOrderBookNotEnoughFundsForMultipleFee() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 600.0)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "BTC", balance = 0.0403)
        initServices()

        for (i in 1..2) {
            singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                    uid = "order$i", walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.01,
                    fees = listOf(buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE,
                            makerSizeType = FeeSizeType.PERCENTAGE,
                            makerSize = BigDecimal.valueOf(0.01),
                            targetWalletId = 3,
                            assetIds = listOf("BTC"))!!))))
            Thread.sleep(10)
        }

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order3", walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.01,
                fees = listOf(
                        buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE,
                                makerSizeType = FeeSizeType.PERCENTAGE,
                                makerSize = BigDecimal.valueOf(0.01),
                                targetWalletId = 3,
                                assetIds = listOf("BTC"))!!,
                        buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE,
                                makerSizeType = FeeSizeType.PERCENTAGE,
                                makerSize = BigDecimal.valueOf(0.01),
                                targetWalletId = 3,
                                assetIds = listOf("BTC"))!!))))

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order4", walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.01,
                fees = listOf(buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE,
                        makerSizeType = FeeSizeType.PERCENTAGE,
                        makerSize = BigDecimal.valueOf(0.01),
                        targetWalletId = 3,
                        assetIds = listOf("BTC"))!!))))

        assertEquals(4, testOrderDatabaseAccessor.getOrders("BTCUSD", false).size)

        clientsEventsQueue.clear()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order", walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = 0.04
        )))

        val orders = (clientsEventsQueue.poll() as ExecutionEvent).orders
        assertEquals(OutgoingOrderStatus.MATCHED, orders.first { it.externalId == "order1" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, orders.first { it.externalId == "order2" }.status)
        assertEquals(OutgoingOrderStatus.CANCELLED, orders.first { it.externalId == "order3" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, orders.first { it.externalId == "order4" }.status)
        assertEquals(OutgoingOrderStatus.PARTIALLY_MATCHED, orders.first { it.externalId == "order" }.status)
        
        assertEquals(BigDecimal.valueOf(0.01), balancesHolder.getBalance(DEFAULT_BROKER, 2, "BTC"))
        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCUSD", false).size)
        assertEquals(1, testOrderDatabaseAccessor.getOrders("BTCUSD", true).size)
    }

    @Test
    fun testMarketNotEnoughFundsForFee1() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 764.99)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "BTC", balance = 0.05)

        initServices()

        for (i in 1..5) {
            singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                    walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.01
            )))
        }

        clientsEventsQueue.clear()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order", walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = 0.05,
                fees = listOf(buildLimitOrderFeeInstruction(
                        type = FeeType.CLIENT_FEE,
                        takerSizeType = FeeSizeType.PERCENTAGE,
                        takerSize = BigDecimal.valueOf(0.02),
                        targetWalletId = 3,
                        assetIds = listOf("USD"))!!))))

        val result = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(OutgoingOrderStatus.REJECTED, result.orders.first { it.externalId == "order" }.status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, result.orders.first { it.externalId == "order" }.rejectReason)
        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCUSD", true).size)
        assertEquals(5, testOrderDatabaseAccessor.getOrders("BTCUSD", false).size)
    }

    @Test
    fun testMarketNotEnoughFundsForFee2() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 764.99)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "BTC", balance = 0.05)

        initServices()

        for (i in 1..5) {
            singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                    walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.01
            )))
        }

        clientsEventsQueue.clear()

        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(
                walletId = 1, assetId = "BTCUSD", volume = 0.05,
                fees = listOf(buildLimitOrderFeeInstruction(
                        type = FeeType.CLIENT_FEE,
                        takerSizeType = FeeSizeType.PERCENTAGE,
                        takerSize = BigDecimal.valueOf(0.02),
                        targetWalletId = 3,
                        assetIds = listOf("USD"))!!))))

        val result = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(OutgoingOrderStatus.REJECTED, result.orders.first().status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, result.orders.first().rejectReason)
        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCUSD", true).size)
        assertEquals(5, testOrderDatabaseAccessor.getOrders("BTCUSD", false).size)
    }

//    @Test
//    fun testMarketNotEnoughFundsForFee3() {
//        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 764.99)
//        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "BTC", balance = 0.05)
//
//        initServices()
//
//        for (i in 1..5) {
//            singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
//                    walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.01
//            )))
//        }
//
//        clientsEventsQueue.clear()
//
//        marketOrderService.processMessage(buildMarketOrderWrapper(buildMarketOrder(
//                walletId = 1, assetId = "BTCUSD", volume = -750.0, straight = false,
//                fees = listOf(buildLimitOrderFeeInstruction(
//                        type = FeeType.CLIENT_FEE,
//                        takerSizeType = FeeSizeType.PERCENTAGE,
//                        takerSize = BigDecimal.valueOf(0.02),
//                        targetWalletId = 3,
//                        assetIds = listOf("USD"))!!))))
//
//        val result = clientsEventsQueue.poll() as ExecutionEvent
//        assertEquals(OutgoingOrderStatus.REJECTED, result.orders.first().status)
//        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, result.orders.first().rejectReason)
//        assertEquals(0, testOrderDatabaseAccessor.getOrders("BTCUSD", true).size)
//        assertEquals(5, testOrderDatabaseAccessor.getOrders("BTCUSD", false).size)
//    }

    @Test
    fun testNotEnoughFundsForFeeOppositeAsset() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 151.5)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "BTC", balance = 0.01521)

        initServices()

        val feeSizes = arrayListOf(0.01, 0.1, 0.01)
        feeSizes.forEachIndexed { index, feeSize ->
            singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                    uid = "order$index", walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.005,
                    fees = listOf(buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE,
                            makerSizeType = FeeSizeType.PERCENTAGE,
                            makerSize = BigDecimal.valueOf(feeSize),
                            targetWalletId = 3,
                            assetIds = listOf("BTC"))!!))))
            Thread.sleep(10)
        }

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order4", walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = 0.01,
                fees = listOf(buildLimitOrderFeeInstruction(
                        type = FeeType.CLIENT_FEE,
                        takerSizeType = FeeSizeType.PERCENTAGE,
                        takerSize = BigDecimal.valueOf(0.02),
                        targetWalletId = 3,
                        assetIds = listOf("USD"))!!))))

        var event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.orders.size)
        assertEquals(1, event.balanceUpdates?.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single {it.externalId == "order1"}.status)
        assertEquals(OutgoingOrderStatus.REJECTED, event.orders.single {it.externalId == "order4"}.status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, event.orders.single {it.externalId == "order4"}.rejectReason)
        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 2)
        assertBalance(2, "BTC", 0.01521, 0.01)

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order5", walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = 0.01,
                fees = listOf(buildLimitOrderFeeInstruction(
                        type = FeeType.CLIENT_FEE,
                        takerSizeType = FeeSizeType.PERCENTAGE,
                        takerSize = BigDecimal.valueOf(0.01),
                        targetWalletId = 3,
                        assetIds = listOf("USD"))!!))))

        event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(3, event.orders.size)
        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "order0" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "order2" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "order5" }.status)

        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 0)
    }

    @Test
    fun testNotEnoughFundsForFeeAnotherAsset() {
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCEUR", "BTC", "EUR", 8))

        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "BTC", balance = 0.015)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "EUR", balance = 1.26)


        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "USD", balance = 150.0)
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "EUR", balance = 1.06)


        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "EURUSD", price = 1.3, volume = -1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "EURUSD", price = 1.1, volume = 1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "BTCEUR", price = 11000.0, volume = -1.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 4, assetId = "BTCEUR", price = 10000.0, volume = 1.0))

        initServices()

        val feeSizes = arrayListOf(0.01, 0.1, 0.01)
        feeSizes.forEachIndexed { index, feeSize ->
            singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                    uid = "order$index", walletId = 2, assetId = "BTCUSD", price = 15000.0, volume = -0.005,
                    fees = listOf(buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE,
                            makerSizeType = FeeSizeType.PERCENTAGE,
                            makerSize = BigDecimal.valueOf(feeSize),
                            targetWalletId = 3,
                            assetIds = listOf("EUR"))!!))))
            Thread.sleep(10)
        }

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order4", walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = 0.01,
                fees = listOf(buildLimitOrderFeeInstruction(
                        type = FeeType.CLIENT_FEE,
                        takerSizeType = FeeSizeType.PERCENTAGE,
                        takerSize = BigDecimal.valueOf(0.02),
                        targetWalletId = 3,
                        assetIds = listOf("EUR"))!!))))

        var event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, event.orders.size)
        assertEquals(1, event.balanceUpdates?.size)
        assertEquals(OutgoingOrderStatus.CANCELLED, event.orders.single { it.externalId == "order1" }.status)
        assertEquals(OutgoingOrderStatus.REJECTED, event.orders.single { it.externalId == "order4" }.status)
        assertEquals(OrderRejectReason.NOT_ENOUGH_FUNDS, event.orders.single { it.externalId == "order4" }.rejectReason)
        assertBalance(2, "BTC", 0.015, 0.01)
        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 2)

        clearMessageQueues()
        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(
                uid = "order5", walletId = 1, assetId = "BTCUSD", price = 15000.0, volume = 0.01,
                fees = listOf(buildLimitOrderFeeInstruction(
                        type = FeeType.CLIENT_FEE,
                        takerSizeType = FeeSizeType.PERCENTAGE,
                        takerSize = BigDecimal.valueOf(0.01),
                        targetWalletId = 3,
                        assetIds = listOf("EUR"))!!))))

        event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "order0" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "order2" }.status)
        assertEquals(OutgoingOrderStatus.MATCHED, event.orders.single { it.externalId == "order5" }.status)

        assertOrderBookSize("BTCUSD", true, 0)
        assertOrderBookSize("BTCUSD", false, 0)
    }

    @Test
    fun testMakerFeeModificator() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "BTC", balance = 0.1)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "USD", balance = 100.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 150, assetId = "BTCUSD", volume = -1.0, price = 10000.0))
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 150, assetId = "BTCUSD", volume = -1.0, price = 11000.0))

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "BTCUSD", volume = 0.01, price = 9700.0,
                fees = listOf(buildLimitOrderFeeInstruction(
                        type = FeeType.CLIENT_FEE,
                        makerSizeType = FeeSizeType.PERCENTAGE,
                        makerSize = BigDecimal.valueOf(0.04),
                        makerFeeModificator = BigDecimal.valueOf(50.0),
                        targetWalletId = 200)!!)))

        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = -0.1, price = 9000.0,
                fees = listOf(buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE, takerSize = BigDecimal.valueOf(0.01), targetWalletId = 200)!!))))

        // 0.01 * 0.04 * (1 - exp(-(10000.0 - 9700.0)/10000.0 * 50.0))
        assertEquals(BigDecimal.valueOf(0.00031075), balancesHolder.getBalance(DEFAULT_BROKER, 200, "BTC"))

        val result = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, result.orders.size)

        assertEquals(1, result.orders.filter { it.walletId == 1L }.size)
        val takerResult = result.orders.first { it.walletId == 1L }
        assertEquals(1, takerResult.trades!!.size)
        assertEquals("300", takerResult.trades!!.first().absoluteSpread)
        assertEquals("0.03", takerResult.trades!!.first().relativeSpread)

        assertEquals(1, takerResult.trades!!.first().fees!!.size)
        assertNull(takerResult.trades!!.first().fees!!.first().feeCoef)

        assertEquals(1, result.orders.filter { it.walletId == 2L }.size)
        val makerResult = result.orders.first { it.walletId == 2L }
        assertEquals(1, makerResult.trades!!.size)
        assertEquals("300", makerResult.trades!!.first().absoluteSpread)
        assertEquals("0.03", makerResult.trades!!.first().relativeSpread)

        assertEquals(1, makerResult.trades!!.first().fees!!.size)
        assertEquals("0.776869839852", makerResult.trades!!.first().fees!!.first().feeCoef)
    }

    @Test
    fun testMakerFeeModificatorForEmptyOppositeOrderBookSide() {
        testBalanceHolderWrapper.updateBalance(walletId = 1, assetId = "BTC", balance = 0.1)
        testBalanceHolderWrapper.updateBalance(walletId = 2, assetId = "USD", balance = 100.0)

        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, assetId = "BTCUSD", volume = 0.01, price = 9700.0,
                fees = listOf(buildLimitOrderFeeInstruction(
                        type = FeeType.CLIENT_FEE,
                        makerSizeType = FeeSizeType.PERCENTAGE,
                        makerSize = BigDecimal.valueOf(0.04),
                        makerFeeModificator = BigDecimal.valueOf(50.0),
                        targetWalletId = 200)!!)))

        initServices()

        singleLimitOrderService.processMessage(messageBuilder.buildLimitOrderWrapper(buildLimitOrder(walletId = 1, assetId = "BTCUSD", volume = -0.1, price = 9000.0,
                fees = listOf(buildLimitOrderFeeInstruction(type = FeeType.CLIENT_FEE, takerSize = BigDecimal.valueOf(0.01), targetWalletId = 200)!!))))

        assertEquals(BigDecimal.valueOf(0.0004), balancesHolder.getBalance(DEFAULT_BROKER, 200, "BTC"))

        val result = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(2, result.orders.size)

        assertEquals(1, result.orders.filter { it.walletId == 1L }.size)
        val takerResult = result.orders.first { it.walletId == 1L }
        assertEquals(1, takerResult.trades!!.size)
        assertNull(takerResult.trades!!.first().absoluteSpread)
        assertNull(takerResult.trades!!.first().relativeSpread)

        assertEquals(1, takerResult.trades!!.first().fees!!.size)
        assertNull(takerResult.trades!!.first().fees!!.first().feeCoef)

        assertEquals(1, result.orders.filter { it.walletId == 2L }.size)
        val makerResult = result.orders.first { it.walletId == 2L }
        assertEquals(1, makerResult.trades!!.size)
        assertNull(makerResult.trades!!.first().absoluteSpread)
        assertNull(makerResult.trades!!.first().relativeSpread)

        assertEquals(1, makerResult.trades!!.first().fees!!.size)
        assertNull(makerResult.trades!!.first().fees!!.first().feeCoef)
    }

    private fun buildLimitOrderFeeInstruction(type: FeeType? = null,
                                              takerSizeType: FeeSizeType? = FeeSizeType.PERCENTAGE,
                                              takerSize: BigDecimal? = null,
                                              makerSizeType: FeeSizeType? = FeeSizeType.PERCENTAGE,
                                              makerSize: BigDecimal? = null,
                                              sourceWalletId: Long? = null,
                                              targetWalletId: Long? = null,
                                              assetIds: List<String> = listOf(),
                                              makerFeeModificator: BigDecimal? = null): NewLimitOrderFeeInstruction? {
        return if (type == null) null
        else return NewLimitOrderFeeInstruction(type, takerSizeType, takerSize, makerSizeType, makerSize, 1000, sourceWalletId, 1000, targetWalletId, assetIds, makerFeeModificator)
    }
}