package com.swisschain.matching.engine.services.validator

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.MarketOrder
import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.services.AssetOrderBook
import com.swisschain.matching.engine.services.validators.MarketOrderValidator
import com.swisschain.matching.engine.services.validators.impl.OrderValidationException
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.getSetting
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
import java.util.UUID
import java.util.concurrent.PriorityBlockingQueue
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (MarketOrderValidatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MarketOrderValidatorTest {

    companion object {
        const val CLIENT_NAME = "Client"
        const val OPERATION_ID = "test"
        const val ASSET_PAIR_ID = "EURUSD"
        const val BASE_ASSET_ID = "EUR"
        const val QUOTING_ASSET_ID = "USD"
    }

    @TestConfiguration
    class Config {
        @Bean
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset(BASE_ASSET_ID, 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset(QUOTING_ASSET_ID, 2))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 2))
            testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair( ASSET_PAIR_ID, BASE_ASSET_ID, QUOTING_ASSET_ID, 2, BigDecimal.valueOf(0.9)))
            return testDictionariesDatabaseAccessor
        }

        @Bean
        @Primary
        fun test(): TestSettingsDatabaseAccessor {
            val testSettingsDatabaseAccessor = TestSettingsDatabaseAccessor()
            testSettingsDatabaseAccessor.createOrUpdateSetting(AvailableSettingGroup.DISABLED_ASSETS, getSetting("BTC"))
            return testSettingsDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var marketOrderValidator: MarketOrderValidator

    @Autowired
    private lateinit var testDictionariesDatabaseAccessor: TestDictionariesDatabaseAccessor

    @Test(expected = OrderValidationException::class)
    fun testUnknownAsset() {
        //given
        val marketOrderBuilder = getDefaultMarketOrderBuilder()
        marketOrderBuilder.assetPairId = "BTCUSD"
        val order = toMarketOrder(marketOrderBuilder.build())

        //when
        try {
            marketOrderValidator.performValidation(order, getOrderBook(order.isBuySide()),listOf( NewFeeInstruction.create(getFeeInstruction())))
        } catch (e: OrderValidationException) {
            assertEquals(OrderStatus.UnknownAsset, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testAssetDisabled() {
        //given
        val marketOrderBuilder = getDefaultMarketOrderBuilder()
        marketOrderBuilder.assetPairId = "BTCUSD"
        val order = toMarketOrder(marketOrderBuilder.build())
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair( "BTCUSD", "BTC", "USD", 2))

        //when
        try {
            marketOrderValidator.performValidation(order, getOrderBook(order.isBuySide()), listOf( NewFeeInstruction.create(getFeeInstruction())))
        } catch (e: OrderValidationException) {
            assertEquals(OrderStatus.DisabledAsset, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidVolume() {
        //given
        val marketOrderBuilder = getDefaultMarketOrderBuilder()
        marketOrderBuilder.volume = "0.1"
        val order = toMarketOrder(marketOrderBuilder.build())

        //when
        try {
            marketOrderValidator.performValidation(order,  getOrderBook(order.isBuySide()), listOf( NewFeeInstruction.create(getFeeInstruction())))
        } catch (e: OrderValidationException) {
            assertEquals(OrderStatus.TooSmallVolume, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidFee() {
        //given
        val marketOrderBuilder = getDefaultMarketOrderBuilder()
        val order = toMarketOrder(marketOrderBuilder.build())

        //when
        try {
            marketOrderValidator.performValidation(order, getOrderBook(order.isBuySide()),
                    listOf( NewFeeInstruction.create(getInvalidFee())))
        } catch (e: OrderValidationException) {
            assertEquals(OrderStatus.InvalidFee, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun invalidOrderBook() {
        //given
        val marketOrderBuilder = getDefaultMarketOrderBuilder()
        val order = toMarketOrder(marketOrderBuilder.build())

        //when
        try {
            marketOrderValidator.performValidation(order,  AssetOrderBook(DEFAULT_BROKER, ASSET_PAIR_ID).getOrderBook(order.isBuySide()),
                    listOf( NewFeeInstruction.create(getFeeInstruction())))
        } catch (e: OrderValidationException) {
            assertEquals(OrderStatus.NoLiquidity, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidVolumeAccuracy() {
        //given
        val marketOrderBuilder = getDefaultMarketOrderBuilder()
        marketOrderBuilder.volume = "1.1111"
        val order = toMarketOrder(marketOrderBuilder.build())

        //when
        try {
            marketOrderValidator.performValidation(order, getOrderBook(order.isBuySide()), listOf( NewFeeInstruction.create(getFeeInstruction())))
        } catch (e: OrderValidationException) {
            assertEquals(OrderStatus.InvalidVolumeAccuracy, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidPriceAccuracy() {
        //given
        val marketOrderBuilder = getDefaultMarketOrderBuilder()
        val order = toMarketOrder(marketOrderBuilder.build())
        order.price = BigDecimal.valueOf(1.1111)

        //when
        try {
            marketOrderValidator.performValidation(order, getOrderBook(order.isBuySide()), listOf( NewFeeInstruction.create(getFeeInstruction())))
        } catch (e: OrderValidationException) {
            assertEquals(OrderStatus.InvalidPriceAccuracy, e.orderStatus)
            throw e
        }
    }

    @Test
    fun testValidData() {
        //given
        val marketOrderBuilder = getDefaultMarketOrderBuilder()
        val order = toMarketOrder(marketOrderBuilder.build())

        //when
        marketOrderValidator.performValidation(order, getOrderBook(order.isBuySide()), listOf( NewFeeInstruction.create(getFeeInstruction())))
    }

    private fun toMarketOrder(message: IncomingMessages.MarketOrder): MarketOrder {
        val now = Date()
        return MarketOrder(UUID.randomUUID().toString(), message.uid, message.assetPairId, DEFAULT_BROKER, message.walletId, BigDecimal(message.volume), null,
                OrderStatus.Processing.name, now, Date(message.timestamp.seconds), now, null, message.straight,
                if (message.hasReservedLimitVolume()) BigDecimal(message.reservedLimitVolume.value) else null,
                listOf(NewFeeInstruction.create(message.feesList.first())))
    }

    private fun getOrderBook(isBuy: Boolean): PriorityBlockingQueue<LimitOrder> {
        val assetOrderBook = AssetOrderBook(DEFAULT_BROKER, ASSET_PAIR_ID)
        val now = Date()
        assetOrderBook.addOrder(LimitOrder("test", "test",
                ASSET_PAIR_ID, DEFAULT_BROKER, CLIENT_NAME, BigDecimal.valueOf(1.0), BigDecimal.valueOf(1.0),
                OrderStatus.InOrderBook.name, now, now, now, BigDecimal.valueOf(1.0), now, BigDecimal.valueOf(1.0),
                null, null, null, null, null, null, null, null,
                null, null, null))

        return assetOrderBook.getOrderBook(isBuy)
     }

    private fun getDefaultMarketOrderBuilder(): IncomingMessages.MarketOrder.Builder {
        return IncomingMessages.MarketOrder.newBuilder()
                .setUid(OPERATION_ID)
                .setAssetPairId("EURUSD")
                .setTimestamp(Date().createProtobufTimestampBuilder())
                .setWalletId(CLIENT_NAME)
                .setVolume("1.0")
                .setStraight(true)
                .addFees(getFeeInstruction())
    }

    private fun getFeeInstruction(): IncomingMessages.Fee {
        return  IncomingMessages.Fee.newBuilder()
                .setType(FeeType.NO_FEE.externalId)
                .build()
    }

    private fun getInvalidFee(): IncomingMessages.Fee {
        return  IncomingMessages.Fee.newBuilder()
                .setType(FeeType.EXTERNAL_FEE.externalId)
                .build()
    }
}