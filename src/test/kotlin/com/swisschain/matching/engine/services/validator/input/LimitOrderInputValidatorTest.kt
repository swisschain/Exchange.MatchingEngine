package com.swisschain.matching.engine.services.validator.input

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.daos.context.SingleLimitOrderContext
import com.swisschain.matching.engine.daos.fee.v2.NewLimitOrderFeeInstruction
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.incoming.parsers.data.SingleLimitOrderParsedData
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.services.validators.impl.OrderValidationException
import com.swisschain.matching.engine.services.validators.input.LimitOrderInputValidator
import com.swisschain.matching.engine.utils.DictionariesInit
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

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (LimitOrderInputValidatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LimitOrderInputValidatorTest {
    companion object {
        val NON_EXISTENT_ASSET_PAIR = DictionariesInit.createAssetPair("BTCOOO", "BTC", "OOO", 8)
        val DISABLED_ASSET_PAIR = DictionariesInit.createAssetPair("JPYUSD", "JPY", "USD", 8)
        val MIN_VOLUME_ASSET_PAIR = DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5,
                BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.2))
        val MAX_VALUE_ASSET_PAIR = DictionariesInit.createAssetPair("EURCAD", "EUR", "CAD", 2, maxValue = BigDecimal.valueOf(5.0))
        val BTC_USD_ASSET_PAIR = DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8, maxVolume = BigDecimal.valueOf(10000.0))
    }

    @Autowired
    private lateinit var limitOrderInputValidator: LimitOrderInputValidator

    @Autowired
    private lateinit var testDictionariesDatabaseAccessor: TestDictionariesDatabaseAccessor

    @TestConfiguration
    class Config {

        @Bean
        @Primary
        fun testSettingsDatabaseAccessor(): TestSettingsDatabaseAccessor {
            val testConfigDatabaseAccessor = TestSettingsDatabaseAccessor()
            testConfigDatabaseAccessor.createOrUpdateSetting (AvailableSettingGroup.DISABLED_ASSETS, getSetting("JPY"))
            return testConfigDatabaseAccessor
        }
    }


    @Before
    fun init() {
        testDictionariesDatabaseAccessor.addAssetPair(MIN_VOLUME_ASSET_PAIR)
        testDictionariesDatabaseAccessor.addAssetPair(BTC_USD_ASSET_PAIR)
        testDictionariesDatabaseAccessor.addAssetPair(DISABLED_ASSET_PAIR)
    }

    fun testEmptyPrice() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getStopOrder(null, null, null, null))
        singleLimitContextBuilder.assetPair(MIN_VOLUME_ASSET_PAIR)
        //when
        try {
            limitOrderInputValidator.validateStopOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), MIN_VOLUME_ASSET_PAIR.symbol))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidPrice, e.orderStatus)
            throw e
        }
    }

    @Test(expected = Exception::class)
    fun testAssetDoesNotExist() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), NON_EXISTENT_ASSET_PAIR.symbol))
        singleLimitContextBuilder.assetPair(null)
        singleLimitContextBuilder.baseAsset(null)
        singleLimitContextBuilder.quotingAsset(null)

        //when
        limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), NON_EXISTENT_ASSET_PAIR.symbol))
    }


    @Test(expected = OrderValidationException::class)
    fun testInvalidDisabledAssets() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), "JPYUSD"))
        singleLimitContextBuilder.assetPair(DISABLED_ASSET_PAIR)
        singleLimitContextBuilder.baseAsset(DictionariesInit.createAsset("JPY", 2))
        singleLimitContextBuilder.quotingAsset(DictionariesInit.createAsset("USD", 2))

        //when
        try {
            limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), DISABLED_ASSET_PAIR.symbol))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.DisabledAsset, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidPrice() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), price = BigDecimal.valueOf(-1.0)))

        //when
        try {
            limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), BTC_USD_ASSET_PAIR.symbol))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidPrice, e.orderStatus)
            throw e
        }
    }


    @Test(expected = OrderValidationException::class)
    fun testInvalidVolume() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), assetPair = "EURUSD", volume = BigDecimal.valueOf(0.001)))
        singleLimitContextBuilder.assetPair(MIN_VOLUME_ASSET_PAIR)

        //when
        try {
            limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), "EURUSD"))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.TooSmallVolume, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidValue() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), assetPair = MAX_VALUE_ASSET_PAIR.symbol, volume = BigDecimal.valueOf(1), price = BigDecimal.TEN))
        singleLimitContextBuilder.assetPair(MAX_VALUE_ASSET_PAIR)

        //when
        try {
            limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), MAX_VALUE_ASSET_PAIR.symbol))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidValue, e.orderStatus)
            throw e
        }

    }

    @Test(expected = OrderValidationException::class)
    fun testStopOrderInvalidValue() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getStopOrder(lowerLimitPrice = BigDecimal.valueOf(8), lowerPrice = BigDecimal.TEN))
        singleLimitContextBuilder.assetPair(MAX_VALUE_ASSET_PAIR)

        //when
        try {
            limitOrderInputValidator.validateStopOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), MAX_VALUE_ASSET_PAIR.symbol))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidValue, e.orderStatus)
            throw e
        }

    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidVolumeEqualsZero() {

        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), assetPair = "EURUSD", volume = BigDecimal.ZERO))
        singleLimitContextBuilder.assetPair(BTC_USD_ASSET_PAIR)

        //when
        try {
            limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), "EURUSD"))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidVolume, e.orderStatus)
            throw e
        }

    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidPriceAccuracy() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), assetPair = "EURUSD", price = BigDecimal.valueOf(0.00000000001)))

        //when
        try {
            limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), "EURUSD"))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidPriceAccuracy, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidStopOrderPricesAccuracy() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), assetPair = "EURUSD",
                type = LimitOrderType.STOP_LIMIT,
                lowerLimitPrice = BigDecimal.valueOf(0.000000001),
                lowerPrice = BigDecimal.valueOf(0.000000001)))

        //when
        try {
            limitOrderInputValidator.validateStopOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), "EURUSD"))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidPriceAccuracy, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidVolumeAccuracy() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee()), volume = BigDecimal.valueOf(1.000000001)))

        //when
        try {
            limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), BTC_USD_ASSET_PAIR.symbol))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidVolumeAccuracy, e.orderStatus)
            throw e
        }
    }

    @Test(expected = OrderValidationException::class)
    fun testInvalidLimitPrice() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getStopOrder(lowerLimitPrice = BigDecimal.valueOf(9500.0), lowerPrice = BigDecimal.valueOf(9000.0),
                upperLimitPrice = BigDecimal.valueOf(9500.0), upperPrice = BigDecimal.valueOf(9100.0)))

        //when
        try {
            limitOrderInputValidator.validateStopOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), BTC_USD_ASSET_PAIR.symbol))
        } catch (e: OrderValidationException) {
            //then
            assertEquals(OrderStatus.InvalidPrice, e.orderStatus)
            throw e
        }
    }

    @Test
    fun testValidLimitOrder() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getLimitOrder(listOf(getNewLimitFee())))

        //when
        limitOrderInputValidator.validateLimitOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), BTC_USD_ASSET_PAIR.symbol))
    }

    @Test
    fun testValidStopOrder() {
        //given
        val singleLimitContextBuilder = getSingleLimitContextBuilder()
        singleLimitContextBuilder.limitOrder(getStopOrder(BigDecimal.ONE, BigDecimal.ONE, null, null))

        //when
        limitOrderInputValidator.validateStopOrder(SingleLimitOrderParsedData(getMessageWrapper(singleLimitContextBuilder.build()), BTC_USD_ASSET_PAIR.symbol))
    }

    fun getMessageWrapper(singleLimitContext: SingleLimitOrderContext): GenericMessageWrapper {
        return GenericMessageWrapper(MessageType.LIMIT_ORDER.type, IncomingMessages.LimitOrder.newBuilder().build(), null, false, null, null, singleLimitContext)
    }

    fun getSingleLimitContextBuilder(): SingleLimitOrderContext.Builder {
        val builder = SingleLimitOrderContext.Builder()

        builder.messageId("test")
                .limitOrder(getLimitOrder(listOf(getNewLimitFee())))
                .assetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))
                .baseAsset(DictionariesInit.createAsset("BTC", 5))
                .quotingAsset(DictionariesInit.createAsset("USD", 2))
                .trustedClient(false)
                .limitAsset(DictionariesInit.createAsset("BTC", 5))
                .cancelOrders(false)
                .processedMessage(ProcessedMessage(MessageType.LIMIT_ORDER.type, 1, "String"))
        return builder
    }

    fun getStopOrder(lowerLimitPrice: BigDecimal? = null, lowerPrice: BigDecimal? = null,
                     upperLimitPrice: BigDecimal? = null, upperPrice: BigDecimal? = null): LimitOrder {
        return LimitOrder("test", "test", "BTCUSD", DEFAULT_BROKER, 999, 999, BigDecimal.ONE,
                BigDecimal.ONE, OrderStatus.InOrderBook.name, Date(), Date(), Date(), BigDecimal.ONE, null,
                fees = listOf(getNewLimitFee()), type = LimitOrderType.STOP_LIMIT,
                lowerLimitPrice = lowerLimitPrice, lowerPrice = lowerPrice,
                upperLimitPrice = upperLimitPrice, upperPrice = upperPrice, previousExternalId = null,
                timeInForce = null,
                expiryTime = null,
                parentOrderExternalId = null,
                childOrderExternalId = null)
    }

    fun getLimitOrder(fees: List<NewLimitOrderFeeInstruction>? = null,
                      assetPair: String = "BTCUSD",
                      price: BigDecimal = BigDecimal.valueOf(1.0),
                      volume: BigDecimal = BigDecimal.valueOf(1.0),
                      type: LimitOrderType = LimitOrderType.LIMIT,
                      lowerLimitPrice: BigDecimal? = null,
                      lowerPrice: BigDecimal? = null,
                      upperLimitPrice: BigDecimal? = null,
                      upperPrice: BigDecimal? = null): LimitOrder {
        return LimitOrder("test", "test", assetPair, DEFAULT_BROKER, 999, 999, volume,
                price, OrderStatus.InOrderBook.name, Date(), Date(), Date(), BigDecimal.valueOf(1.0), null,
                type = type, fees = fees, lowerLimitPrice = lowerLimitPrice, lowerPrice = lowerPrice, upperLimitPrice = upperLimitPrice, upperPrice = upperPrice, previousExternalId = null,
                timeInForce = null,
                expiryTime = null,
                parentOrderExternalId = null,
                childOrderExternalId = null)
    }

    fun getNewLimitFee(): NewLimitOrderFeeInstruction {
        return NewLimitOrderFeeInstruction(FeeType.NO_FEE, null, null, null, null, null, null, null, null, listOf(), null)
    }
}