package com.swisschain.matching.engine.services.validator.input

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.cache.ApplicationSettingsCache
import com.swisschain.matching.engine.incoming.parsers.data.CashTransferParsedData
import com.swisschain.matching.engine.incoming.parsers.impl.CashTransferContextParser
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.services.validators.input.CashTransferOperationInputValidator
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
@SpringBootTest(classes = [(TestApplicationContext::class), (CashTransferOperationInputValidatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CashTransferOperationInputValidatorTest {

    companion object {
        val CLIENT_NAME1 = 1L
        val CLIENT_NAME2 = 2L
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
    private lateinit var cashTransferParser: CashTransferContextParser

    @Autowired
    private lateinit var applicationSettingsCache: ApplicationSettingsCache

    @Autowired
    private lateinit var cashTransferOperationInputValidator: CashTransferOperationInputValidator

    @Autowired
    private lateinit var testBalanceHolderWrapper: TestBalanceHolderWrapper

    @Before
    fun setUp() {
        testBalanceHolderWrapper.updateBalance(CLIENT_NAME1, ASSET_ID, 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(CLIENT_NAME1, ASSET_ID, 50.0)
    }

    @Test(expected = ValidationException::class)
    fun testAssetExists() {
        //given
        val cashTransferOperationBuilder = getCashTransferOperationBuilder()
        cashTransferOperationBuilder.assetId = "UNKNOWN"

        try {
            //when
            cashTransferOperationInputValidator.performValidation(getParsedData(cashTransferOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.UNKNOWN_ASSET, e.validationType)
            throw e
        }
    }

    @Test(expected = ValidationException::class)
    fun testAssetEnabled() {
        //given
        val cashTransferOperationBuilder = getCashTransferOperationBuilder()
        applicationSettingsCache.createOrUpdateSettingValue(AvailableSettingGroup.DISABLED_ASSETS, ASSET_ID, ASSET_ID, true)
        cashTransferOperationBuilder.volume = "-1"

        //when
        try {
            cashTransferOperationInputValidator.performValidation(getParsedData(cashTransferOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.DISABLED_ASSET, e.validationType)
            throw e
        }
    }

    @Test(expected = ValidationException::class)
    fun testInvalidFee() {
        //given
        val cashTransferOperationBuilder = getCashTransferOperationBuilder()

        //when
        try {
            val invalidFee = IncomingMessages.Fee.newBuilder()
                    .setType(FeeType.EXTERNAL_FEE.externalId).build()


            cashTransferOperationBuilder.addFees(invalidFee)
            cashTransferOperationInputValidator.performValidation(getParsedData(cashTransferOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.INVALID_FEE, e.validationType)
            throw e
        }
    }

    @Test(expected = ValidationException::class)
    fun testVolumeAccuracy() {
        //given
        val cashTransferOperationBuilder = getCashTransferOperationBuilder()
        cashTransferOperationBuilder.volume = "10.001"

        //when
        try {
            cashTransferOperationInputValidator.performValidation(getParsedData(cashTransferOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.INVALID_VOLUME_ACCURACY, e.validationType)
            throw e
        }
    }

    @Test
    fun testValidData() {
        //when
        cashTransferOperationInputValidator.performValidation(getParsedData(getCashTransferOperationBuilder().build()))
    }

    fun getCashTransferOperationBuilder(): IncomingMessages.CashTransferOperation.Builder {
        return IncomingMessages.CashTransferOperation
                .newBuilder()
                .setBrokerId(DEFAULT_BROKER)
                .setId("test")
                .setAssetId(ASSET_ID)
                .setTimestamp(Date().createProtobufTimestampBuilder())
                .setFromWalletId(CLIENT_NAME1)
                .setToWalletId(CLIENT_NAME2).setVolume("0")
    }

    private fun getMessageWrapper(message: IncomingMessages.CashTransferOperation): GenericMessageWrapper {
        return GenericMessageWrapper(MessageType.CASH_TRANSFER_OPERATION.type, message, null)
    }

    private fun getParsedData(message: IncomingMessages.CashTransferOperation): CashTransferParsedData{
        return cashTransferParser.parse(getMessageWrapper(message))
    }
}
