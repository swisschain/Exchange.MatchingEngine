package com.swisschain.matching.engine.holders

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.cache.AssetPairsCache
import com.swisschain.matching.engine.services.DisabledFunctionalityRulesService
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.web.dto.DeleteSettingRequestDto
import com.swisschain.matching.engine.web.dto.DisabledFunctionalityRuleDto
import com.swisschain.matching.engine.web.dto.OperationType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (DisabledFunctionalityRulesHolderTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DisabledFunctionalityRulesHolderTest {

    @Autowired
    private lateinit var disabledFunctionalityRulesHolder: DisabledFunctionalityRulesHolder

    @Autowired
    private lateinit var disabledFunctionalityRulesService: DisabledFunctionalityRulesService

    @Autowired
    private lateinit var assetsPairsHolder: AssetsPairsHolder

    @Autowired
    private lateinit var assetsHolder: AssetsHolder

    @Autowired
    private lateinit var  assetPairsCache: AssetPairsCache

    @Autowired
    private lateinit var testDictionariesDatabaseAccessor: TestDictionariesDatabaseAccessor

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 4))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("EUR", 4))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("JPY", 4))
            testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 2))
            testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("EURJPY", "EUR", "JPY", 2))
            return testDictionariesDatabaseAccessor
        }
    }

    @Test
    fun fullMatchTest() {
        //given
        disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null, DEFAULT_BROKER, "BTC", null, OperationType.CASH_IN.name, true, "test", "test"))

        //then
        assertTrue(disabledFunctionalityRulesHolder.isCashInDisabled(assetsHolder.getAsset( DEFAULT_BROKER, "BTC")))
    }

    @Test
    fun matchAssetAndAssetPairTest() {
        //given
        disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null,  DEFAULT_BROKER, "BTC", null, OperationType.TRADE.name, true, "test", "test"))

        //then
        assertTrue(disabledFunctionalityRulesHolder.isTradeDisabled(assetsPairsHolder.getAssetPair( DEFAULT_BROKER, "BTCUSD")))
    }

    @Test
    fun notMatchTest() {
        //given
        disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null,  DEFAULT_BROKER, "BTC", null, OperationType.CASH_TRANSFER.name, true, "test", "test"))

        //then
        assertFalse(disabledFunctionalityRulesHolder.isCashTransferDisabled(assetsHolder.getAsset( DEFAULT_BROKER, "JPY")))
    }

    @Test
    fun disabledRuleDoesNotMatchTest() {
        //given
        disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null,  DEFAULT_BROKER, null, "BTCUSD", OperationType.TRADE.name, false, "test", "test"))

        //then
        assertFalse(disabledFunctionalityRulesHolder.isTradeDisabled(assetsPairsHolder.getAssetPair( DEFAULT_BROKER, "BTCUSD")))
    }

    @Test
    fun removedRuleDoesNotMatch() {
        //given
        val ruleId = disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null,  DEFAULT_BROKER, "BTC", null, OperationType.TRADE.name, true, "test", "test"))

        //when
        disabledFunctionalityRulesService.delete(ruleId, DeleteSettingRequestDto("test", "test"))

        //then
        assertFalse(disabledFunctionalityRulesHolder.isTradeDisabled(assetsPairsHolder.getAssetPair( DEFAULT_BROKER, "BTCUSD")))
    }

    @Test
    fun assetMatchWithAnyMessageType() {
        //given
        disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null,  DEFAULT_BROKER, "BTC", null, null, true, "test", "test"))

        //then
        assertTrue(disabledFunctionalityRulesHolder.isTradeDisabled(assetsPairsHolder.getAssetPair(DEFAULT_BROKER, "BTCUSD")))
        assertTrue(disabledFunctionalityRulesHolder.isCashTransferDisabled(assetsHolder.getAsset( DEFAULT_BROKER, "BTC")))
    }

    @Test
    fun operationMatchTest() {
        //given
        disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null,  DEFAULT_BROKER, null, null, OperationType.CASH_IN.name, true, "test", "test"))

        //then
        assertTrue(disabledFunctionalityRulesHolder.isCashInDisabled(assetsHolder.getAsset( DEFAULT_BROKER, "BTC")))
    }

    @Test
    fun newAssetPairAdded() {
        //given
        disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null,  DEFAULT_BROKER, "JPY", null, OperationType.TRADE.name, true, "test", "test"))

        //when
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCJPY", "BTC", "JPY", 6))
        assetPairsCache.update()
        disabledFunctionalityRulesHolder.update()

        //then
        assertTrue(disabledFunctionalityRulesHolder.isTradeDisabled(assetsPairsHolder.getAssetPair(DEFAULT_BROKER, "BTCJPY")))
    }

    @Test
    fun assetPairAddedRemovedThenAgainAdded() {
        //given
        disabledFunctionalityRulesService.create(DisabledFunctionalityRuleDto(null,  DEFAULT_BROKER, "JPY", null, OperationType.TRADE.name, true, "test", "test"))

        //when
        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCJPY", "BTC", "JPY", 6))
        assetPairsCache.update()
        disabledFunctionalityRulesHolder.update()
        assertTrue(disabledFunctionalityRulesHolder.isTradeDisabled(assetsPairsHolder.getAssetPair(DEFAULT_BROKER, "BTCJPY")))

        testDictionariesDatabaseAccessor.clear()
        assetPairsCache.update()

        testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCJPY", "BTC", "JPY", 6))
        assetPairsCache.update()

        //then
        assertTrue(disabledFunctionalityRulesHolder.isTradeDisabled(assetsPairsHolder.getAssetPair(DEFAULT_BROKER, "BTCJPY")))
    }
}