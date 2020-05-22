package com.swisschain.matching.engine.services.validator

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.services.validators.ReservedCashInOutOperationValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
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
@SpringBootTest(classes = [(TestApplicationContext::class), (ReservedCashInOutOperationValidatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReservedCashInOutOperationValidatorTest {

    companion object {
        val CLIENT_NAME = 0L
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
    private lateinit var testBalanceHolderWrapper: TestBalanceHolderWrapper

    @Autowired
    private lateinit var reservedCashInOutOperationValidator: ReservedCashInOutOperationValidator

    @Before
    fun int() {
        testBalanceHolderWrapper.updateReservedBalance(CLIENT_NAME, ASSET_ID, 500.0)
        testBalanceHolderWrapper.updateBalance(CLIENT_NAME, ASSET_ID, 550.0)
    }

    @Test(expected = ValidationException::class)
    fun testVolumeAccuracyInvalid() {
        //given
        val message = getDefaultReservedOperationMessageBuilder()
                .setReservedVolume("1.111")
                .build()

        //when
        try {
            reservedCashInOutOperationValidator.performValidation(message)
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.INVALID_VOLUME_ACCURACY, e.validationType)
            throw e
        }
    }

    @Test(expected = ValidationException::class)
    fun testBalanceInvalid() {
        //given
        val message = getDefaultReservedOperationMessageBuilder()
                .setReservedVolume("-550")
                .build()


        //when
        try {
            reservedCashInOutOperationValidator.performValidation(message)
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.LOW_BALANCE, e.validationType)
            throw e
        }
    }

    @Test(expected = ValidationException::class)
    fun testReservedBalanceInvalid() {
        //given
        val message = getDefaultReservedOperationMessageBuilder()
                .setReservedVolume("51")
                .build()

        //when
        try {
            reservedCashInOutOperationValidator.performValidation(message)
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.RESERVED_VOLUME_HIGHER_THAN_BALANCE, e.validationType)
            throw e
        }
    }

    @Test
    fun testValidData() {
        //given
        val message = getDefaultReservedOperationMessageBuilder()
                .build()

        //when
        reservedCashInOutOperationValidator.performValidation(message)
    }


    private fun getDefaultReservedOperationMessageBuilder(): IncomingMessages.ReservedCashInOutOperation.Builder {
        return IncomingMessages.ReservedCashInOutOperation.newBuilder()
                .setBrokerId(DEFAULT_BROKER)
                .setId("test")
                .setWalletId(CLIENT_NAME)
                .setTimestamp(Date().createProtobufTimestampBuilder())
                .setAssetId(ASSET_ID)
                .setReservedVolume("0")
    }
}