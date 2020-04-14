package com.swisschain.matching.engine.services.validator

import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.services.validators.common.OrderValidationUtils
import com.swisschain.matching.engine.services.validators.impl.OrderValidationException
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import org.junit.Assert
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderValidationUtilsTest {
    private companion object {
        val MIN_VOLUME_ASSET_PAIR = DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5,
                BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.2))
        val BTC_USD_ASSET_PAIR = DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8)
    }
    
    @Test
    fun testCheckVolume() {
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(assetId = "BTCUSD", volume = 1.0), BTC_USD_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(assetId = "BTCUSD", volume = 0.1), BTC_USD_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(assetId = "BTCUSD", volume = 0.00000001), BTC_USD_ASSET_PAIR) }

        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(volume = 1.0), MIN_VOLUME_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(volume = 0.1), MIN_VOLUME_ASSET_PAIR) }
        assertFalse { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(volume = 0.09), MIN_VOLUME_ASSET_PAIR) }

        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(volume = -1.0), MIN_VOLUME_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(volume = -0.1), MIN_VOLUME_ASSET_PAIR) }
        assertFalse { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(volume = -0.09), MIN_VOLUME_ASSET_PAIR) }

        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(price = 1.0, volume = 1.0), MIN_VOLUME_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(price = 1.0, volume = 0.1), MIN_VOLUME_ASSET_PAIR) }
        assertFalse { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(price = 1.0, volume = 0.09), MIN_VOLUME_ASSET_PAIR) }

        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(price = 1.0, volume = -1.0), MIN_VOLUME_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(price = 1.0, volume = -0.1), MIN_VOLUME_ASSET_PAIR) }
        assertFalse { OrderValidationUtils.checkMinVolume(MessageBuilder.buildLimitOrder(price = 1.0, volume = -0.09), MIN_VOLUME_ASSET_PAIR) }

        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(assetId = "BTCUSD", volume = 1.0), BTC_USD_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(assetId = "BTCUSD", volume = 0.1), BTC_USD_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(assetId = "BTCUSD", volume = 0.00000001), BTC_USD_ASSET_PAIR) }

        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(volume = 1.0), MIN_VOLUME_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(volume = 0.1), MIN_VOLUME_ASSET_PAIR) }
        assertFalse { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(volume = 0.09), MIN_VOLUME_ASSET_PAIR) }

        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(volume = -1.0), MIN_VOLUME_ASSET_PAIR) }
        assertTrue { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(volume = -0.1), MIN_VOLUME_ASSET_PAIR) }
        assertFalse { OrderValidationUtils.checkMinVolume(MessageBuilder.buildMarketOrder(volume = -0.09), MIN_VOLUME_ASSET_PAIR) }
    }


    @Test(expected = OrderValidationException::class)
    fun testInvalidBalance() {
        try {
            //when
            OrderValidationUtils.validateBalance(BigDecimal.valueOf(10.0), BigDecimal.valueOf(11.0))
        } catch (e: OrderValidationException) {
            //then
            Assert.assertEquals(OrderStatus.NotEnoughFunds, e.orderStatus)
            throw e
        }
    }

    @Test
    fun testValidBalance() {
        //when
        OrderValidationUtils.validateBalance(BigDecimal.valueOf(10.0), BigDecimal.valueOf(9.0))
    }
}