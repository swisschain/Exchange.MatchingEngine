package com.swisschain.matching.engine.performance

import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.PrintUtils
import org.junit.Ignore
import org.junit.Test
import java.math.BigDecimal

@Ignore
class MarketOrderPerformanceTest: AbstractPerformanceTest() {

    override fun initServices() {
        super.initServices()

        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("LKK", 0))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("SLR", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("GBP", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("CHF", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("JPY", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC1", 8))

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURJPY", "EUR", "JPY", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCCHF", "BTC", "CHF", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCLKK", "BTC", "LKK", 6))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTC1USD", "BTC1", "USD", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCCHF", "BTC", "CHF", 3))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("SLRBTC", "SLR", "BTC", 8))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("LKKEUR", "LKK", "EUR", 5))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("LKKGBP", "LKK", "GBP", 5))
    }

    @Test
    fun testPerformance() {
        val averageOrderProcessionTime = Runner.runTests(REPEAT_TIMES, ::testNoLiqudity,
                ::testNotEnoughFundsClientOrder, ::testNotEnoughFundsClientMultiOrder, ::testNoLiqudityToFullyFill,
                ::testNotEnoughFundsMarketOrder, ::testSmallVolume, ::testMatchOneToOne, ::testMatchOneToOneAfterNotEnoughFunds,
                ::testMatchOneToMany, ::testMatchOneToOneEURJPY, ::testMatchOneToMany2016Dec12, ::testNotStraight,
                ::testNotStraightMatchOneToMany)

        println("Market order average processing time is: ${PrintUtils.convertToString2(averageOrderProcessionTime)}")
    }

    fun testNoLiqudity(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 1))
        initServices()

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder())) }

        return counter.getAverageTime()
    }

    fun testNotEnoughFundsClientOrder(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.6, volume = 1000.0, walletId = 1))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 2))
        initServices()

        counter.executeAction {marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 3, assetId = "EURUSD", volume = -1000.0)))}
        return counter.getAverageTime()
    }

    fun testNotEnoughFundsClientMultiOrder(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.6, volume = 1000.0, walletId = 1))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 1))

        initServices()

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 3, assetId = "EURUSD", volume = -1500.0))) }

        return counter.getAverageTime()
    }

    fun testNoLiqudityToFullyFill(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 2))
        initServices()
        testBalanceHolderWrapper.updateBalance(2, "USD", 1500.0)
        testBalanceHolderWrapper.updateBalance(3, "EUR", 2000.0)

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 3, assetId = "EURUSD", volume = -2000.0))) }
        return counter.getAverageTime()
    }

    fun testNotEnoughFundsMarketOrder(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 3))
        initServices()

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0))) }
        return counter.getAverageTime()
    }

    fun testSmallVolume(): Double {
        val counter = ActionTimeCounter()

        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 2))
        testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 2))
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURUSD", "EUR", "USD",
                5, BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.2)))
        initServices()

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(volume = 0.09))) }
        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(volume = -0.19, straight = false))) }
        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(volume = 0.2, straight = false))) }

        return counter.getAverageTime()
    }

    fun testMatchOneToOne(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 3))
        initServices()
        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)
        testBalanceHolderWrapper.updateReservedBalance(3, "USD",1500.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 1000.0)


        counter.executeAction {marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0)))}
        return counter.getAverageTime()
    }

    fun testMatchOneToOneEURJPY(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "EURJPY", price = 122.512, volume = 1000000.0, walletId = 3))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "EURJPY", price = 122.524, volume = -1000000.0, walletId = 3))
        initServices()
        testBalanceHolderWrapper.updateBalance(3, "JPY", 5000000.0)
        testBalanceHolderWrapper.updateBalance(3, "EUR", 5000000.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 0.1)
        testBalanceHolderWrapper.updateBalance(4, "JPY", 100.0)

        counter.executeAction {marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "EURJPY", volume = 10.0, straight = false)))}

        return counter.getAverageTime()
    }

    fun testMatchOneToOneAfterNotEnoughFunds(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = 1000.0, walletId = 3))
        initServices()
        testBalanceHolderWrapper.updateBalance(3, "USD", 1500.0)

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0))) }
        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0))) }
        return counter.getAverageTime()
    }

    fun testMatchOneToMany(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = 100.0, walletId = 3))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.4, volume = 1000.0, walletId = 1))
        initServices()
        testBalanceHolderWrapper.updateBalance(1, "USD", 1560.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD",1400.0)
        testBalanceHolderWrapper.updateBalance(3, "USD", 150.0)
        testBalanceHolderWrapper.updateBalance(4, "EUR", 1000.0)

        counter.executeAction {marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1000.0)))}
        return counter.getAverageTime()
    }

    fun testMatchOneToMany2016Dec12(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "SLRBTC", price = 0.00008826, volume = -4000.0, walletId = 1))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "SLRBTC", price = 0.00008844, volume = -4000.0, walletId = 1))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "SLRBTC", price = 0.00008861, volume = -4000.0, walletId = 1))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "SLRBTC", price = 0.00008879, volume = -4000.0, walletId = 1))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "SLRBTC", price = 0.00008897, volume = -4000.0, walletId = 1))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "SLRBTC", price = 0.00008914, volume = -4000.0, walletId = 1))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(assetId = "SLRBTC", price = 0.00008932, volume = -4000.0, walletId = 1))
        initServices()
        testBalanceHolderWrapper.updateBalance(1, "SLR", 100000.0)
        testBalanceHolderWrapper.updateBalance(4, "BTC", 31.95294)

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "SLRBTC", volume = 25000.0, straight = true))) }

        return counter.getAverageTime()
    }

    fun testNotStraight(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = -500.0, assetId = "EURUSD", walletId = 3))
        initServices()
        testBalanceHolderWrapper.updateBalance(3, "EUR", 500.0)
        testBalanceHolderWrapper.updateBalance(4, "USD", 750.0)

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -750.0, straight = false))) }
        return counter.getAverageTime()
    }

    fun testNotStraightMatchOneToMany(): Double {
        val counter = ActionTimeCounter()

        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.4, volume = -100.0, walletId = 3))
        primaryOrdersDatabaseAccessor.addLimitOrder(MessageBuilder.buildLimitOrder(price = 1.5, volume = -1000.0, walletId = 1))
        initServices()
        testBalanceHolderWrapper.updateBalance(1, "EUR", 3000.0)
        testBalanceHolderWrapper.updateBalance(3, "EUR", 3000.0)
        testBalanceHolderWrapper.updateBalance(4, "USD", 2000.0)

        counter.executeAction { marketOrderService.processMessage(MessageBuilder.buildMarketOrderWrapper(MessageBuilder.buildMarketOrder(walletId = 4, assetId = "EURUSD", volume = -1490.0, straight = false))) }
        return counter.getAverageTime()
    }
}