package com.swisschain.matching.engine.services.validator.settings

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.services.validators.settings.impl.DisabledFunctionalitySettingValidator
import com.swisschain.matching.engine.utils.DictionariesInit
import com.swisschain.matching.engine.web.dto.DisabledFunctionalityRuleDto
import com.swisschain.matching.engine.web.dto.OperationType
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
@SpringBootTest(classes = [(TestApplicationContext::class), (DisabledFunctionalitySettingValidatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DisabledFunctionalitySettingValidatorTest {

    @TestConfiguration
    open class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAssetPair(DictionariesInit.createAssetPair("BTCUSD", "BTC", "USD", 5))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("BTC", 8))
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset("USD", 4))
            return testDictionariesDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var disabledFunctionalitySettingValidator: DisabledFunctionalitySettingValidator

    @Test(expected = ValidationException::class)
    fun testEmptyRuleIsInvalid() {
        disabledFunctionalitySettingValidator.validate(DisabledFunctionalityRuleDto(null, DEFAULT_BROKER, null, "", null, true, "test", "test"))
    }

    @Test(expected = ValidationException::class)
    fun testAssetDoesNotExist() {
        disabledFunctionalitySettingValidator.validate(DisabledFunctionalityRuleDto(null, DEFAULT_BROKER, "TEST", "", OperationType.CASH_IN.name, true, "test", "test"))
    }

    @Test(expected = ValidationException::class)
    fun testAssetPairDoesNotExist() {
        disabledFunctionalitySettingValidator.validate(DisabledFunctionalityRuleDto(null, DEFAULT_BROKER, "TEST", "", OperationType.CASH_OUT.name, true, "test", "test"))
    }

    @Test(expected = ValidationException::class)
    fun operationDoesNotExist() {
        disabledFunctionalitySettingValidator.validate(DisabledFunctionalityRuleDto(null, DEFAULT_BROKER, "TEST", "", "NOT_EXIST", true, "test", "test"))

    }

    @Test(expected = ValidationException::class)
    fun nonTradOperationSuppliedWithAssetPair() {
        disabledFunctionalitySettingValidator.validate(DisabledFunctionalityRuleDto(null, DEFAULT_BROKER, null, "BTCUSD", OperationType.CASH_OUT.name, true, "test", "test"))
    }

    @Test
    fun validRule() {
        disabledFunctionalitySettingValidator.validate(DisabledFunctionalityRuleDto(null, DEFAULT_BROKER, "BTC", "BTCUSD", OperationType.TRADE.name, true, "test", "test"))
    }
}