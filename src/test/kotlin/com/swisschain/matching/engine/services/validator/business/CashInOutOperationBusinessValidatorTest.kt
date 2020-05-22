package com.swisschain.matching.engine.services.validator.business

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.context.CashInOutContext
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.incoming.parsers.impl.CashInOutContextParser
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.services.validator.input.CashInOutOperationInputValidatorTest
import com.swisschain.matching.engine.services.validators.business.CashInOutOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.utils.DictionariesInit
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
@SpringBootTest(classes = [(TestApplicationContext::class), (CashInOutOperationBusinessValidatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CashInOutOperationBusinessValidatorTest {

    companion object {
        val CLIENT_NAME = 1L
        val ASSET_ID = "USD"
    }

    @TestConfiguration
    class Config {

        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset(CashInOutOperationInputValidatorTest.ASSET_ID, 2))
            return testDictionariesDatabaseAccessor
        }
    }

    @Autowired
    private lateinit var testBalanceHolderWrapper: TestBalanceHolderWrapper

    @Autowired
    private lateinit var cashInOutOperationBusinessValidator: CashInOutOperationBusinessValidator

    @Autowired
    private lateinit var cashInOutContextInitializer: CashInOutContextParser

    @Test(expected = ValidationException::class)
    fun testBalanceValid() {
        //given
        testBalanceHolderWrapper.updateBalance(CLIENT_NAME, ASSET_ID, 500.0)
        testBalanceHolderWrapper.updateReservedBalance(CLIENT_NAME, ASSET_ID, 250.0)
        val cashInOutOperationBuilder = getDefaultCashInOutOperationBuilder()
        cashInOutOperationBuilder.volume = "-300"

        //when
        try {
            cashInOutOperationBusinessValidator.performValidation(getContext(cashInOutOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.LOW_BALANCE, e.validationType)
            throw e
        }
    }

    private fun getDefaultCashInOutOperationBuilder(): IncomingMessages.CashInOutOperation.Builder {
        return IncomingMessages.CashInOutOperation.newBuilder()
                .setId("test")
                .setBrokerId(DEFAULT_BROKER)
                .setWalletId(CLIENT_NAME)
                .setAssetId(ASSET_ID)
                .setVolume("0")
                .setTimestamp(Date().createProtobufTimestampBuilder())
                .addFees(IncomingMessages.Fee.newBuilder()
                        .setType(FeeType.NO_FEE.externalId))
    }

    private fun getMessageWrapper(cashInOutOperation: IncomingMessages.CashInOutOperation): GenericMessageWrapper {
        return GenericMessageWrapper(MessageType.CASH_IN_OUT_OPERATION.type,
                cashInOutOperation,
                null, false)
    }

    private fun getContext(cashInOutOperation: IncomingMessages.CashInOutOperation): CashInOutContext {
        return cashInOutContextInitializer.parse(getMessageWrapper(cashInOutOperation)).messageWrapper.context as CashInOutContext
    }
}