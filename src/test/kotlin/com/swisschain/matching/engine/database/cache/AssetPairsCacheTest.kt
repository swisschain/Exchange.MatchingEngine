package com.swisschain.matching.engine.database.cache

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.utils.DictionariesInit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.assertNull


@RunWith(MockitoJUnitRunner::class)
class AssetPairsCacheTest {

    @Mock
    private lateinit var dictionariesDatabaseAccessor: DictionariesDatabaseAccessor

    @Mock
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Test
    fun testInitialLoading() {
        //given
        val btcUsdAssetPair = DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5)
        val eurUsdAssetPair = DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2)

        Mockito.`when`(dictionariesDatabaseAccessor.loadAssetPairs())
                .thenReturn(hashMapOf(DEFAULT_BROKER to hashMapOf("BTCUSD" to btcUsdAssetPair,
                        "EURUSD" to eurUsdAssetPair)))

        //when
        val assetPairsCache = AssetPairsCache(dictionariesDatabaseAccessor)

        //then
        assertEquals(btcUsdAssetPair, assetPairsCache.getAssetPair(DEFAULT_BROKER, "BTCUSD"))
        assertEquals(eurUsdAssetPair, assetPairsCache.getAssetPair(DEFAULT_BROKER, "EURUSD"))
    }

    @Test
    fun testNewAssetAdded() {
        //given
        val btcUsdAssetPair = DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5)
        val eurUsdAssetPair = DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2)

        Mockito.`when`(dictionariesDatabaseAccessor.loadAssetPairs())
                .thenReturn(hashMapOf(DEFAULT_BROKER to hashMapOf("BTCUSD" to btcUsdAssetPair)))

        //when
        val assetPairsCache = AssetPairsCache(dictionariesDatabaseAccessor, 50)
        assertEquals(btcUsdAssetPair, assetPairsCache.getAssetPair(DEFAULT_BROKER, "BTCUSD"))

        Mockito.`when`(dictionariesDatabaseAccessor.loadAssetPairs())
                .thenReturn(hashMapOf(DEFAULT_BROKER to hashMapOf("BTCUSD" to btcUsdAssetPair,
                        "EURUSD" to eurUsdAssetPair)))
        Thread.sleep(100)


        //then
        assertEquals(eurUsdAssetPair, assetPairsCache.getAssetPair(DEFAULT_BROKER, "EURUSD"))
        assertEquals(btcUsdAssetPair, assetPairsCache.getAssetPair(DEFAULT_BROKER, "BTCUSD"))
    }

    @Test
    fun testAssetRemoved() {
        //given
        val btcUsdAssetPair = DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5)
        val eurUsdAssetPair = DictionariesInit.createAssetPair("EURUSD", "EUR", "USD", 2)

        Mockito.`when`(dictionariesDatabaseAccessor.loadAssetPairs())
                .thenReturn(hashMapOf(DEFAULT_BROKER to hashMapOf("BTCUSD" to btcUsdAssetPair,
                        "EURUSD" to eurUsdAssetPair)))

        //when
        val assetPairsCache = AssetPairsCache(dictionariesDatabaseAccessor,  50)
        assertEquals(btcUsdAssetPair, assetPairsCache.getAssetPair(DEFAULT_BROKER, "BTCUSD"))
        assertEquals(eurUsdAssetPair, assetPairsCache.getAssetPair(DEFAULT_BROKER, "EURUSD"))

        Mockito.`when`(dictionariesDatabaseAccessor.loadAssetPairs())
                .thenReturn(hashMapOf(DEFAULT_BROKER to hashMapOf("BTCUSD" to btcUsdAssetPair)))
        Thread.sleep(100)


        //then
        assertEquals(btcUsdAssetPair, assetPairsCache.getAssetPair(DEFAULT_BROKER, "BTCUSD"))
        assertNull(assetPairsCache.getAssetPair(DEFAULT_BROKER, "EURUSD"))
    }
}