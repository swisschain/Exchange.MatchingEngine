package com.swisschain.matching.engine.services.validator.business

import com.google.protobuf.StringValue
import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.context.CashTransferContext
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.incoming.parsers.impl.CashTransferContextParser
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import com.swisschain.matching.engine.services.validators.business.CashTransferOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.utils.DictionariesInit
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import java.util.Date
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class), (CashTransferOperationBusinessValidatorTest.Config::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CashTransferOperationBusinessValidatorTest {

    companion object {
        val CLIENT_NAME1 = "Client1"
        val CLIENT_NAME2 = "Client2"
        val ASSET_ID = "USD"
    }

    @Autowired
    private lateinit var cashTransferOperationBusinessValidator: CashTransferOperationBusinessValidator

    @Qualifier("cashTransferInitializer")
    @Autowired
    private lateinit var cashTransferParser: CashTransferContextParser

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun testDictionariesDatabaseAccessor(): DictionariesDatabaseAccessor {
            val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
            testDictionariesDatabaseAccessor.addAsset(DictionariesInit.createAsset(ASSET_ID, 2))
            return testDictionariesDatabaseAccessor
        }

        @Bean
        @Primary
        fun testBalanceHolderWrapper(balancesHolder: BalancesHolder): TestBalanceHolderWrapper {
            val testBalanceHolderWrapper = TestBalanceHolderWrapper(balancesHolder)
            testBalanceHolderWrapper.updateBalance(CLIENT_NAME1, ASSET_ID, 100.0)
            testBalanceHolderWrapper.updateReservedBalance(CLIENT_NAME1, ASSET_ID, 50.0)
            return testBalanceHolderWrapper
        }
    }

    @Test
    fun testLowBalanceHighOverdraftLimit() {
        //given
        val cashTransferOperationBuilder = getCashTransferOperationBuilder()
        cashTransferOperationBuilder.overdraftLimit = StringValue.of("40")
        cashTransferOperationBuilder.volume = "60"

        //when
        cashTransferOperationBusinessValidator.performValidation(getContext(cashTransferOperationBuilder.build()))
    }

    @Test(expected = ValidationException::class)
    fun testLowBalance() {
        //given
        val cashTransferOperationBuilder = getCashTransferOperationBuilder()
        cashTransferOperationBuilder.volume = "60"
        cashTransferOperationBuilder.overdraftLimit = StringValue.of("0")

        //when
        try {
            cashTransferOperationBusinessValidator.performValidation(getContext(cashTransferOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.LOW_BALANCE, e.validationType)
            throw e
        }
    }

    @Test
    fun testPositiveOverdraftLimit() {
        //given
        val cashTransferOperationBuilder = getCashTransferOperationBuilder()
        cashTransferOperationBuilder.volume = "30"
        cashTransferOperationBuilder.overdraftLimit = StringValue.of("1")

        //when
        cashTransferOperationBusinessValidator.performValidation(getContext(cashTransferOperationBuilder.build()))
    }

    @Test(expected = ValidationException::class)
    fun testNegativeOverdraftLimit() {
        val cashTransferOperationBuilder = getCashTransferOperationBuilder()
        cashTransferOperationBuilder.volume = "60"
        cashTransferOperationBuilder.overdraftLimit = StringValue.of("-1")

        //when
        try {
            cashTransferOperationBusinessValidator.performValidation(getContext(cashTransferOperationBuilder.build()))
        } catch (e: ValidationException) {
            assertEquals(ValidationException.Validation.LOW_BALANCE, e.validationType)
            throw e
        }
    }


    fun getCashTransferOperationBuilder(): IncomingMessages.CashTransferOperation.Builder {
        return IncomingMessages.CashTransferOperation
                .newBuilder()
                .setBrokerId(DEFAULT_BROKER)
                .setId("test")
                .setAssetId(ASSET_ID)
                .setTimestamp(Date().createProtobufTimestampBuilder())
                .setFromWalletId(CLIENT_NAME1)
                .setToWalletId(CLIENT_NAME2).setVolume("10")
    }

    private fun getMessageWrapper(message: IncomingMessages.CashTransferOperation): GenericMessageWrapper {
        return GenericMessageWrapper(MessageType.CASH_TRANSFER_OPERATION.type, message, null)
    }

    private fun getContext(message: IncomingMessages.CashTransferOperation): CashTransferContext {
        return cashTransferParser.parse(getMessageWrapper(message)).messageWrapper.context as CashTransferContext
    }
}