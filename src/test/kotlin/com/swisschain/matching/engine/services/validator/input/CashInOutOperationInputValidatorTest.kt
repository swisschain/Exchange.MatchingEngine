package com.swisschain.matching.engine.services.validator.input

import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.cache.ApplicationSettingsCache
import com.swisschain.matching.engine.incoming.parsers.data.CashInOutParsedData
import com.swisschain.matching.engine.incoming.parsers.impl.CashInOutContextParser
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.services.validators.input.CashInOutOperationInputValidator
import com.swisschain.matching.engine.utils.DictionariesInit
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
import java.util.Date
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (CashInOutOperationInputValidatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CashInOutOperationInputValidatorTest {

    companion object {
        val CLIENT_ID = "Client1"
        val ASSET_ID = "USD"
    }

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset(ASSET_ID, 2))
            return testDictionariesDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var cashInOutOperationInputValidator: CashInOutOperationInputValidator

    @Autowired
    private lateinit var cashInOutContextInitializer: CashInOutContextParser

    @Autowired
    private lateinit var applicationSettingsCache: ApplicationSettingsCache

    @Autowired
    private lateinit var testBalanceHolderWrapper: TestBalanceHolderWrapper

    @Before
    fun setUp() {
        testBalanceHolderWrapper.updateBalance(CLIENT_ID, ASSET_ID, 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(CLIENT_ID, ASSET_ID, 50.0)
    }

    @Test(expected = ValidationException::class)
    fun assetDoesNotExist() {
        //given
    val cashInOutOperationBuilder = getDefaultCashInOutOperationBuilder()
        cashInOutOperationBuilder.setAssetId("UNKNOWN")

        try {
            //when
            cashInOutOperationInputValidator
                    .performValidation(getParsedData(cashInOutOperationBuilder.build()))
        } catch (e: ValidationException) {
            //then
            assertEquals(ValidationException.Validation.UNKNOWN_ASSET, e.validationType)
            throw e
        }
    }

    @Test(expected = ValidationException::class)
    fun testInvalidFee() {
        //given
        val cashInOutOperationBuilder = getDefaultCashInOutOperationBuilder()

        //when
        try {
            val fee = IncomingMessages.Fee.newBuilder()
                    .setType(FeeType.EXTERNAL_FEE.externalId).build()
            cashInOutOperationBuilder.addFees(fee)
            val cashInOutContext = getParsedData(cashInOutOperationBuilder.build())
            cashInOutOperationInputValidator
                    .performValidation(cashInOutContext)
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.INVALID_FEE, e.validationType)
            throw e
        }

    }

    @Test(expected = ValidationException::class)
    fun testAssetEnabled() {
        //given
        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.DISABLED_ASSETS, ASSET_ID, ASSET_ID, true)

        //when
        try {
            val cashInOutOperationBuilder = getDefaultCashInOutOperationBuilder()
            cashInOutOperationBuilder.volume = "-1"
            cashInOutOperationInputValidator.performValidation(getParsedData(cashInOutOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.DISABLED_ASSET, e.validationType)
            throw e
        }
    }

    @Test(expected = ValidationException::class)
    fun testVolumeAccuracy() {
        //given
        val cashInOutOperationBuilder = getDefaultCashInOutOperationBuilder()
        cashInOutOperationBuilder.volume = "10.001"

        //when
        try {
            cashInOutOperationInputValidator.performValidation(getParsedData(cashInOutOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.INVALID_VOLUME_ACCURACY, e.validationType)
            throw e
        }
    }

    @Test
    fun validData() {
        cashInOutOperationInputValidator.performValidation(getParsedData(getDefaultCashInOutOperationBuilder().build()))
    }

    private fun getDefaultCashInOutOperationBuilder(): IncomingMessages.CashInOutOperation.Builder {
        return IncomingMessages.CashInOutOperation.newBuilder()
                .setId("test")
                .setWalletId(CLIENT_ID)
                .setAssetId(ASSET_ID)
                .setVolume("0")
                .setTimestamp(Date().createProtobufTimestampBuilder())
                .addFees(IncomingMessages.Fee.newBuilder()
                        .setType(FeeType.NO_FEE.externalId))
    }

    private fun getMessageWrapper(cashInOutOperation: IncomingMessages.CashInOutOperation): GenericMessageWrapper {
        return GenericMessageWrapper(MessageType.CASH_IN_OUT_OPERATION.type,
                cashInOutOperation,
                null)
    }

    private fun getParsedData(cashInOutOperation: IncomingMessages.CashInOutOperation): CashInOutParsedData {
        return cashInOutContextInitializer.parse(getMessageWrapper(cashInOutOperation))
    }
}