package com.swisschain.matching.engine.config

import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.config.spring.JsonConfig
import com.swisschain.matching.engine.config.spring.QueueConfig
import com.swisschain.matching.engine.daos.TradeInfo
import com.swisschain.matching.engine.daos.setting.AvailableSettingGroup
import com.swisschain.matching.engine.database.CashOperationIdDatabaseAccessor
import com.swisschain.matching.engine.database.DictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.ReadOnlyMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.SettingsDatabaseAccessor
import com.swisschain.matching.engine.database.SettingsHistoryDatabaseAccessor
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestFileOrderDatabaseAccessor
import com.swisschain.matching.engine.database.TestFileStopOrderDatabaseAccessor
import com.swisschain.matching.engine.database.TestMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.TestOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.TestPersistenceManager
import com.swisschain.matching.engine.database.TestReservedVolumesDatabaseAccessor
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.database.TestStopOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.TestWalletDatabaseAccessor
import com.swisschain.matching.engine.database.WalletDatabaseAccessor
import com.swisschain.matching.engine.database.cache.ApplicationSettingsCache
import com.swisschain.matching.engine.database.cache.AssetPairsCache
import com.swisschain.matching.engine.database.cache.AssetsCache
import com.swisschain.matching.engine.deduplication.ProcessedMessagesCache
import com.swisschain.matching.engine.fee.FeeProcessor
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.DisabledFunctionalityRulesHolder
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.holders.OrderBookMaxTotalSizeHolder
import com.swisschain.matching.engine.holders.OrderBookMaxTotalSizeHolderImpl
import com.swisschain.matching.engine.holders.OrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.holders.StopOrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.holders.TestUUIDHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.incoming.MessageRouter
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderCancelOperationParsedData
import com.swisschain.matching.engine.incoming.parsers.data.LimitOrderMassCancelOperationParsedData
import com.swisschain.matching.engine.incoming.parsers.impl.CashInOutContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.CashTransferContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.LimitOrderCancelOperationContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.LimitOrderMassCancelOperationContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.SingleLimitOrderContextParser
import com.swisschain.matching.engine.incoming.preprocessor.impl.CashInOutPreprocessor
import com.swisschain.matching.engine.incoming.preprocessor.impl.CashTransferPreprocessor
import com.swisschain.matching.engine.incoming.preprocessor.impl.SingleLimitOrderPreprocessor
import com.swisschain.matching.engine.matching.MatchingEngine
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.notification.SettingsListener
import com.swisschain.matching.engine.order.ExecutionDataApplyService
import com.swisschain.matching.engine.order.ExpiryOrdersQueue
import com.swisschain.matching.engine.order.process.GenericLimitOrdersProcessor
import com.swisschain.matching.engine.order.process.PreviousLimitOrdersProcessor
import com.swisschain.matching.engine.order.process.StopOrderBookProcessor
import com.swisschain.matching.engine.order.process.common.LimitOrdersCancelExecutor
import com.swisschain.matching.engine.order.process.common.MatchingResultHandlingHelper
import com.swisschain.matching.engine.order.transaction.ExecutionContextFactory
import com.swisschain.matching.engine.order.utils.TestOrderBookWrapper
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import com.swisschain.matching.engine.outgoing.senders.OutgoingEventProcessor
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSender
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSendersHolder
import com.swisschain.matching.engine.outgoing.senders.impl.SpecializedEventSendersHolderImpl
import com.swisschain.matching.engine.services.ApplicationSettingsService
import com.swisschain.matching.engine.services.ApplicationSettingsServiceImpl
import com.swisschain.matching.engine.services.CashInOutOperationService
import com.swisschain.matching.engine.services.CashTransferOperationService
import com.swisschain.matching.engine.services.DisabledFunctionalityRulesService
import com.swisschain.matching.engine.services.DisabledFunctionalityRulesServiceImpl
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.services.GenericStopLimitOrderService
import com.swisschain.matching.engine.services.LimitOrderCancelService
import com.swisschain.matching.engine.services.LimitOrderMassCancelService
import com.swisschain.matching.engine.services.LimitOrdersCancelServiceHelper
import com.swisschain.matching.engine.services.MarketOrderService
import com.swisschain.matching.engine.services.MessageSender
import com.swisschain.matching.engine.services.MultiLimitOrderService
import com.swisschain.matching.engine.services.ReservedCashInOutOperationService
import com.swisschain.matching.engine.services.SingleLimitOrderService
import com.swisschain.matching.engine.services.validators.MarketOrderValidator
import com.swisschain.matching.engine.services.validators.ReservedCashInOutOperationValidator
import com.swisschain.matching.engine.services.validators.business.CashInOutOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.business.CashTransferOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.business.LimitOrderBusinessValidator
import com.swisschain.matching.engine.services.validators.business.LimitOrderCancelOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.business.impl.CashInOutOperationBusinessValidatorImpl
import com.swisschain.matching.engine.services.validators.business.impl.CashTransferOperationBusinessValidatorImpl
import com.swisschain.matching.engine.services.validators.business.impl.LimitOrderBusinessValidatorImpl
import com.swisschain.matching.engine.services.validators.business.impl.LimitOrderCancelOperationBusinessValidatorImpl
import com.swisschain.matching.engine.services.validators.business.impl.StopOrderBusinessValidatorImpl
import com.swisschain.matching.engine.services.validators.impl.MarketOrderValidatorImpl
import com.swisschain.matching.engine.services.validators.impl.ReservedCashInOutOperationValidatorImpl
import com.swisschain.matching.engine.services.validators.input.CashInOutOperationInputValidator
import com.swisschain.matching.engine.services.validators.input.CashTransferOperationInputValidator
import com.swisschain.matching.engine.services.validators.input.LimitOrderCancelOperationInputValidator
import com.swisschain.matching.engine.services.validators.input.LimitOrderInputValidator
import com.swisschain.matching.engine.services.validators.input.impl.CashInOutOperationInputValidatorImpl
import com.swisschain.matching.engine.services.validators.input.impl.CashTransferOperationInputValidatorImpl
import com.swisschain.matching.engine.services.validators.input.impl.LimitOrderCancelOperationInputValidatorImpl
import com.swisschain.matching.engine.services.validators.input.impl.LimitOrderInputValidatorImpl
import com.swisschain.matching.engine.services.validators.settings.SettingValidator
import com.swisschain.matching.engine.services.validators.settings.impl.DisabledFunctionalitySettingValidator
import com.swisschain.matching.engine.services.validators.settings.impl.MessageProcessingSwitchSettingValidator
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.matching.engine.utils.balance.ReservedVolumesRecalculator
import com.swisschain.matching.engine.utils.monitoring.HealthMonitor
import com.swisschain.matching.engine.utils.order.AllOrdersCanceller
import com.swisschain.matching.engine.utils.order.MinVolumeOrderCanceller
import com.swisschain.utils.logging.ThrottlingLogger
import org.mockito.Mockito
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue

@Configuration
@Import(QueueConfig::class, TestExecutionContext::class, JsonConfig::class)
class TestApplicationContext {

    @Bean
    fun tradeInfoQueue(): BlockingQueue<TradeInfo> {
        return LinkedBlockingQueue<TradeInfo>()
    }

    @Bean
    fun threadPoolTaskExecutor(): Executor {
        val threadPoolTaskExecutor = ThreadPoolTaskExecutor()
        threadPoolTaskExecutor.threadNamePrefix = "executor-task"
        threadPoolTaskExecutor.corePoolSize = 2
        threadPoolTaskExecutor.maxPoolSize = 2

        return threadPoolTaskExecutor
    }

    @Bean
    fun outgoingPublishersThreadPool(): TaskExecutor {
        val threadPoolTaskExecutor = ThreadPoolTaskExecutor()
        threadPoolTaskExecutor.threadNamePrefix = "outgoing-task"
        threadPoolTaskExecutor.corePoolSize = Integer.MAX_VALUE

        return threadPoolTaskExecutor
    }

    @Bean
    fun balanceHolder(persistenceManager: PersistenceManager,
                           applicationSettingsHolder: ApplicationSettingsHolder,
                           testDictionariesDatabaseAccessor: TestDictionariesDatabaseAccessor): BalancesHolder {
        return BalancesHolder(walletDatabaseAccessor, persistenceManager, assetHolder(testDictionariesDatabaseAccessor),
                applicationSettingsHolder)
    }

    val walletDatabaseAccessor = TestWalletDatabaseAccessor()
    @Bean
    fun walletDatabaseAccessor(): WalletDatabaseAccessor {
        return walletDatabaseAccessor
    }

    @Bean
    fun assetHolder(dictionariesDatabaseAccessor: DictionariesDatabaseAccessor): AssetsHolder {
        return AssetsHolder(assetCache(dictionariesDatabaseAccessor))
    }

    @Bean
    fun applicationSettingsHolder(applicationSettingsCache: ApplicationSettingsCache): ApplicationSettingsHolder {
        return ApplicationSettingsHolder(applicationSettingsCache)
    }

    @Bean
    fun messageSequenceNumberHolder(messageSequenceNumberDatabaseAccessor: ReadOnlyMessageSequenceNumberDatabaseAccessor): MessageSequenceNumberHolder {
        return MessageSequenceNumberHolder(messageSequenceNumberDatabaseAccessor)
    }

    @Bean
    fun notificationSender(clientsEventsQueue: BlockingQueue<Event>,
                                trustedClientsEventsQueue: BlockingQueue<ExecutionEvent>,
                                outgoingOrderBookQueue: BlockingQueue<OrderBookEvent>): MessageSender {
        return MessageSender(clientsEventsQueue, trustedClientsEventsQueue, outgoingOrderBookQueue)
    }

    @Bean
    fun reservedVolumesRecalculator(testOrderDatabaseAccessorHolder: OrdersDatabaseAccessorsHolder,
                                         stopOrdersDatabaseAccessorsHolder: StopOrdersDatabaseAccessorsHolder,
                                         testReservedVolumesDatabaseAccessor: TestReservedVolumesDatabaseAccessor,
                                         assetHolder: AssetsHolder, assetsPairsHolder: AssetsPairsHolder,
                                         balancesHolder: BalancesHolder, applicationSettingsHolder: ApplicationSettingsHolder,
                                         messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                         messageSender: MessageSender): ReservedVolumesRecalculator {

        return ReservedVolumesRecalculator(testOrderDatabaseAccessorHolder, stopOrdersDatabaseAccessorsHolder,
                testReservedVolumesDatabaseAccessor, assetHolder,
                assetsPairsHolder, balancesHolder, applicationSettingsHolder,
                false, messageSequenceNumberHolder, messageSender)
    }

    @Bean
    fun testMessageSequenceNumberDatabaseAccessor(): TestMessageSequenceNumberDatabaseAccessor {
        return TestMessageSequenceNumberDatabaseAccessor()
    }

    @Bean
    fun testReservedVolumesDatabaseAccessor(): TestReservedVolumesDatabaseAccessor {
        return TestReservedVolumesDatabaseAccessor()
    }

    @Bean
    fun assetCache(dictionariesDatabaseAccessor: DictionariesDatabaseAccessor): AssetsCache {
        return AssetsCache(dictionariesDatabaseAccessor)
    }
    @Bean
    fun testSettingsDatabaseAccessor(): SettingsDatabaseAccessor {
        return TestSettingsDatabaseAccessor()
    }

    @Bean
    fun testDictionariesDatabaseAccessor(): TestDictionariesDatabaseAccessor {
        return TestDictionariesDatabaseAccessor()
    }

    @Bean
    fun applicationSettingsCache(configDatabaseAccessor: SettingsDatabaseAccessor,
                                      applicationEventPublisher: ApplicationEventPublisher): ApplicationSettingsCache {
        return ApplicationSettingsCache(configDatabaseAccessor, applicationEventPublisher)
    }

    @Bean
    fun testBalanceHolderWrapper(balancesHolder: BalancesHolder): TestBalanceHolderWrapper {
        return TestBalanceHolderWrapper(balancesHolder)
    }

    @Bean
    fun ordersDatabaseAccessorsHolder(testOrderBookDatabaseAccessor: TestOrderBookDatabaseAccessor,
                                           testFileOrderDatabaseAccessor: TestFileOrderDatabaseAccessor): OrdersDatabaseAccessorsHolder {
        return OrdersDatabaseAccessorsHolder(testOrderBookDatabaseAccessor, testFileOrderDatabaseAccessor)
    }

    @Bean
    fun testOrderBookDatabaseAccessor(testFileOrderDatabaseAccessor: TestFileOrderDatabaseAccessor): TestOrderBookDatabaseAccessor {
        return TestOrderBookDatabaseAccessor(testFileOrderDatabaseAccessor)
    }

    @Bean
    fun stopOrdersDatabaseAccessorsHolder(testStopOrderBookDatabaseAccessor: TestStopOrderBookDatabaseAccessor,
                                               testFileStopOrderDatabaseAccessor: TestFileStopOrderDatabaseAccessor): StopOrdersDatabaseAccessorsHolder {
        return StopOrdersDatabaseAccessorsHolder(testStopOrderBookDatabaseAccessor, testFileStopOrderDatabaseAccessor)
    }

    @Bean
    fun persistenceManager(ordersDatabaseAccessorsHolder: OrdersDatabaseAccessorsHolder,
                                stopOrdersDatabaseAccessorsHolder: StopOrdersDatabaseAccessorsHolder): PersistenceManager {
        return TestPersistenceManager(walletDatabaseAccessor,
                ordersDatabaseAccessorsHolder,
                stopOrdersDatabaseAccessorsHolder)
    }

    @Bean
    fun cashInOutOperationBusinessValidator(balancesHolder: BalancesHolder): CashInOutOperationBusinessValidator {
        return CashInOutOperationBusinessValidatorImpl(balancesHolder)
    }

    @Bean
    fun cashTransferOperationBusinessValidator(balancesHolder: BalancesHolder): CashTransferOperationBusinessValidator {
        return CashTransferOperationBusinessValidatorImpl(balancesHolder)
    }

    @Bean
    fun cashInOutOperationInputValidator(applicationSettingsHolder: ApplicationSettingsHolder): CashInOutOperationInputValidator {
        return CashInOutOperationInputValidatorImpl(applicationSettingsHolder)
    }

    @Bean
    fun cashTransferOperationInputValidator(applicationSettingsHolder: ApplicationSettingsHolder): CashTransferOperationInputValidator {
        return CashTransferOperationInputValidatorImpl(applicationSettingsHolder)
    }

    @Bean
    fun disabledFunctionality(assetsHolder: AssetsHolder, assetsPairsHolder: AssetsPairsHolder): DisabledFunctionalitySettingValidator {
        return DisabledFunctionalitySettingValidator(assetsHolder, assetsPairsHolder)
    }

    @Bean
    fun cashInOutOperationService(balancesHolder: BalancesHolder,
                                       feeProcessor: FeeProcessor,
                                       cashInOutOperationBusinessValidator: CashInOutOperationBusinessValidator,
                                       messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                       outgoingEventProcessor: OutgoingEventProcessor): CashInOutOperationService {
        return CashInOutOperationService(balancesHolder,
                feeProcessor,
                cashInOutOperationBusinessValidator,
                messageSequenceNumberHolder,
                outgoingEventProcessor)
    }

    @Bean
    fun marketOrderValidator(assetsPairsHolder: AssetsPairsHolder,
                                  assetsHolder: AssetsHolder,
                                  applicationSettingsHolder: ApplicationSettingsHolder): MarketOrderValidator {
        return MarketOrderValidatorImpl(assetsPairsHolder, assetsHolder, applicationSettingsHolder)
    }

    @Bean
    fun assetPairsCache(testDictionariesDatabaseAccessor: DictionariesDatabaseAccessor,
                             applicationEventPublisher: ApplicationEventPublisher): AssetPairsCache {
        return AssetPairsCache(testDictionariesDatabaseAccessor)
    }

    @Bean
    fun assetPairHolder(assetPairsCache: AssetPairsCache): AssetsPairsHolder {
        return AssetsPairsHolder(assetPairsCache)
    }

    @Bean
    fun reservedCashInOutOperationValidator(balancesHolder: BalancesHolder,
                                                 assetsHolder: AssetsHolder): ReservedCashInOutOperationValidator {
        return ReservedCashInOutOperationValidatorImpl(assetsHolder, balancesHolder)
    }

    @Bean
    fun reservedCashInOutOperation(balancesHolder: BalancesHolder,
                                        assetsHolder: AssetsHolder,
                                        reservedCashInOutOperationValidator: ReservedCashInOutOperationValidator,
                                        messageProcessingStatusHolder: MessageProcessingStatusHolder,
                                        uuidHolder: UUIDHolder,
                                        messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                        outgoingEventProcessor: OutgoingEventProcessor): ReservedCashInOutOperationService {
        return ReservedCashInOutOperationService(assetsHolder,
                balancesHolder,
                reservedCashInOutOperationValidator,
                messageProcessingStatusHolder,
                uuidHolder,
                messageSequenceNumberHolder,
                outgoingEventProcessor)
    }

    @Bean
    fun applicationSettingsHistoryDatabaseAccessor(): SettingsHistoryDatabaseAccessor {
        return Mockito.mock(SettingsHistoryDatabaseAccessor::class.java)
    }

    @Bean
    fun applicationSettingsService(settingsDatabaseAccessor: SettingsDatabaseAccessor,
                                        applicationSettingsCache: ApplicationSettingsCache,
                                        settingsHistoryDatabaseAccessor: SettingsHistoryDatabaseAccessor,
                                        applicationEventPublisher: ApplicationEventPublisher): ApplicationSettingsService {
        return ApplicationSettingsServiceImpl(settingsDatabaseAccessor, applicationSettingsCache, settingsHistoryDatabaseAccessor, applicationEventPublisher)
    }

    @Bean
    fun disabledFunctionalityRulesHolder(applicationSettingsCache: ApplicationSettingsCache,
                                              assetsPairsHolder: AssetsPairsHolder): DisabledFunctionalityRulesHolder {
        return DisabledFunctionalityRulesHolder(applicationSettingsCache, assetsPairsHolder)
    }

    @Bean
    fun genericLimitOrderService(testOrderDatabaseAccessor: OrdersDatabaseAccessorsHolder,
                                      expiryOrdersQueue: ExpiryOrdersQueue): GenericLimitOrderService {
        return GenericLimitOrderService(testOrderDatabaseAccessor,
                expiryOrdersQueue)
    }

    @Bean
    fun singleLimitOrderService(executionContextFactory: ExecutionContextFactory,
                                     genericLimitOrdersProcessor: GenericLimitOrdersProcessor,
                                     stopOrderBookProcessor: StopOrderBookProcessor,
                                     executionDataApplyService: ExecutionDataApplyService,
                                     previousLimitOrdersProcessor: PreviousLimitOrdersProcessor): SingleLimitOrderService {
        return SingleLimitOrderService(executionContextFactory,
                genericLimitOrdersProcessor,
                stopOrderBookProcessor,
                executionDataApplyService,
                previousLimitOrdersProcessor)
    }

    @Bean
    fun multiLimitOrderService(genericLimitOrdersProcessor: GenericLimitOrdersProcessor,
                                    executionContextFactory: ExecutionContextFactory,
                                    previousLimitOrdersProcessor: PreviousLimitOrdersProcessor,
                                    stopOrderBookProcessor: StopOrderBookProcessor,
                                    executionDataApplyService: ExecutionDataApplyService,
                                    assetsHolder: AssetsHolder,
                                    assetsPairsHolder: AssetsPairsHolder,
                                    balancesHolder: BalancesHolder,
                                    applicationSettingsHolder: ApplicationSettingsHolder,
                                    messageProcessingStatusHolder: MessageProcessingStatusHolder,
                                    testUUIDHolder: TestUUIDHolder): MultiLimitOrderService {
        return MultiLimitOrderService(executionContextFactory,
                genericLimitOrdersProcessor,
                stopOrderBookProcessor,
                executionDataApplyService,
                previousLimitOrdersProcessor,
                assetsHolder,
                assetsPairsHolder,
                balancesHolder,
                applicationSettingsHolder,
                messageProcessingStatusHolder,
                testUUIDHolder)
    }

    @Bean
    fun testUUIDHolder() = TestUUIDHolder()

    @Bean
    fun marketOrderService(matchingEngine: MatchingEngine,
                                executionContextFactory: ExecutionContextFactory,
                                stopOrderBookProcessor: StopOrderBookProcessor,
                                executionDataApplyService: ExecutionDataApplyService,
                                matchingResultHandlingHelper: MatchingResultHandlingHelper,
                                genericLimitOrderService: GenericLimitOrderService,
                                assetsPairsHolder: AssetsPairsHolder,
                                messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                messageSender: MessageSender,
                                marketOrderValidator: MarketOrderValidator,
                                applicationSettingsHolder: ApplicationSettingsHolder,
                                messageProcessingStatusHolder: MessageProcessingStatusHolder,
                                uuidHolder: UUIDHolder): MarketOrderService {
        return MarketOrderService(matchingEngine,
                executionContextFactory,
                stopOrderBookProcessor,
                executionDataApplyService,
                matchingResultHandlingHelper,
                genericLimitOrderService,
                messageSequenceNumberHolder,
                messageSender,
                assetsPairsHolder,
                marketOrderValidator,
                applicationSettingsHolder,
                messageProcessingStatusHolder,
                uuidHolder)
    }

    @Bean
    fun minVolumeOrderCanceller(assetsPairsHolder: AssetsPairsHolder,
                                     genericLimitOrderService: GenericLimitOrderService,
                                     limitOrdersCancelExecutor: LimitOrdersCancelExecutor): MinVolumeOrderCanceller {
        return MinVolumeOrderCanceller(assetsPairsHolder,
                genericLimitOrderService,
                limitOrdersCancelExecutor,
                true)
    }

    @Bean
    fun genericStopLimitOrderService(stopOrdersDatabaseAccessorsHolder: StopOrdersDatabaseAccessorsHolder,
                                          expiryOrdersQueue: ExpiryOrdersQueue): GenericStopLimitOrderService {
        return GenericStopLimitOrderService(stopOrdersDatabaseAccessorsHolder, expiryOrdersQueue)
    }

    @Bean
    fun testStopOrderBookDatabaseAccessor(testFileStopOrderDatabaseAccessor: TestFileStopOrderDatabaseAccessor): TestStopOrderBookDatabaseAccessor {
        return TestStopOrderBookDatabaseAccessor(testFileStopOrderDatabaseAccessor)
    }

    @Bean
    fun testFileOrderDatabaseAccessor(): TestFileOrderDatabaseAccessor {
        return TestFileOrderDatabaseAccessor()
    }

    @Bean
    fun testFileStopOrderDatabaseAccessor(): TestFileStopOrderDatabaseAccessor {
        return TestFileStopOrderDatabaseAccessor()
    }

    @Bean
    fun testOrderBookWrapper(genericLimitOrderService: GenericLimitOrderService,
                                  testOrderBookDatabaseAccessor: TestOrderBookDatabaseAccessor,
                                  genericStopLimitOrderService: GenericStopLimitOrderService,
                                  stopOrderBookDatabaseAccessor: TestStopOrderBookDatabaseAccessor): TestOrderBookWrapper {
        return TestOrderBookWrapper(genericLimitOrderService, testOrderBookDatabaseAccessor, genericStopLimitOrderService, stopOrderBookDatabaseAccessor)
    }

    @Bean
    fun allOrdersCanceller(genericLimitOrderService: GenericLimitOrderService,
                                genericStopLimitOrderService: GenericStopLimitOrderService,
                                limitOrdersCancelExecutor: LimitOrdersCancelExecutor): AllOrdersCanceller {
        return AllOrdersCanceller(genericLimitOrderService,
                genericStopLimitOrderService,
                limitOrdersCancelExecutor,
                true)
    }

    @Bean
    fun feeProcessor(assetsHolder: AssetsHolder, assetsPairsHolder: AssetsPairsHolder, genericLimitOrderService: GenericLimitOrderService): FeeProcessor {
        return FeeProcessor(assetsHolder, assetsPairsHolder, genericLimitOrderService)
    }

    @Bean
    fun cashInOutContextParser(assetsHolder: AssetsHolder, uuidHolder: UUIDHolder): CashInOutContextParser {
        return CashInOutContextParser(assetsHolder, uuidHolder)
    }

    @Bean
    fun processedMessagesCache(): ProcessedMessagesCache {
        return Mockito.mock(ProcessedMessagesCache::class.java)
    }

    @Bean
    fun cashInOutPreprocessor(cashInOutContextParser: CashInOutContextParser,
                                   persistenceManager: PersistenceManager,
                                   processedMessagesCache: ProcessedMessagesCache,
                                   messageProcessingStatusHolder: MessageProcessingStatusHolder): CashInOutPreprocessor {
        return CashInOutPreprocessor(cashInOutContextParser,
                LinkedBlockingQueue(),
                Mockito.mock(CashOperationIdDatabaseAccessor::class.java),
                persistenceManager,
                processedMessagesCache,
                messageProcessingStatusHolder,
                ThrottlingLogger.getLogger("cashInOut"))
    }

    @Bean
    fun cashTransferInitializer(assetsHolder: AssetsHolder, uuidHolder: UUIDHolder): CashTransferContextParser {
        return CashTransferContextParser(assetsHolder, uuidHolder)
    }

    @Bean
    fun healthMonitor(): HealthMonitor {
        return Mockito.mock(HealthMonitor::class.java) {
            true
        }
    }

    @Bean
    fun messageProcessingStatusHolder(generalHealthMonitor: HealthMonitor,
                                           applicationSettingsHolder: ApplicationSettingsHolder,
                                           disabledFunctionalityRulesHolder: DisabledFunctionalityRulesHolder): MessageProcessingStatusHolder {
        return MessageProcessingStatusHolder(generalHealthMonitor, applicationSettingsHolder, disabledFunctionalityRulesHolder)
    }

    @Bean
    fun cashTransferPreprocessor(cashTransferContextParser: CashTransferContextParser,
                                      persistenceManager: PersistenceManager,
                                      processedMessagesCache: ProcessedMessagesCache,
                                      messageProcessingStatusHolder: MessageProcessingStatusHolder): CashTransferPreprocessor {
        return CashTransferPreprocessor(cashTransferContextParser,
                LinkedBlockingQueue(),
                Mockito.mock(CashOperationIdDatabaseAccessor::class.java),
                persistenceManager,
                processedMessagesCache,
                messageProcessingStatusHolder,
                ThrottlingLogger.getLogger("transfer"))
    }

    @Bean
    fun messageBuilder(cashTransferContextParser: CashTransferContextParser,
                            cashInOutContextParser: CashInOutContextParser,
                            singleLimitOrderContextParser: SingleLimitOrderContextParser,
                            limitOrderCancelOperationContextParser: ContextParser<LimitOrderCancelOperationParsedData>,
                            limitOrderMassCancelOperationContextParser: ContextParser<LimitOrderMassCancelOperationParsedData>): MessageBuilder {
        return MessageBuilder(singleLimitOrderContextParser, cashInOutContextParser, cashTransferContextParser,
                limitOrderCancelOperationContextParser, limitOrderMassCancelOperationContextParser)
    }

    @Bean
    fun cashTransferOperationService(balancesHolder: BalancesHolder, feeProcessor: FeeProcessor,
                                          cashTransferOperationBusinessValidator: CashTransferOperationBusinessValidator,
                                          messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                          outgoingEventProcessor: OutgoingEventProcessor): CashTransferOperationService {
        return CashTransferOperationService(balancesHolder,  feeProcessor,
                cashTransferOperationBusinessValidator, messageSequenceNumberHolder, outgoingEventProcessor)
    }

    @Bean
    fun settingsListener(): SettingsListener {
        return SettingsListener()
    }

    @Bean
    fun messageProcessingSwitchSettingValidator(): SettingValidator {
        return MessageProcessingSwitchSettingValidator()
    }

    @Bean
    fun settingValidators(settingValidators: List<SettingValidator>): Map<AvailableSettingGroup, List<SettingValidator>> {
        return settingValidators.groupBy { it.getSettingGroup() }
    }

    @Bean
    fun singleLimitOrderContextParser(assetsPairsHolder: AssetsPairsHolder,
                                           assetsHolder: AssetsHolder,
                                           applicationSettingsHolder: ApplicationSettingsHolder,
                                           uuidHolder: UUIDHolder): SingleLimitOrderContextParser {
        return SingleLimitOrderContextParser(assetsPairsHolder,
                assetsHolder,
                applicationSettingsHolder,
                uuidHolder,
                ThrottlingLogger.getLogger("limitOrder"))
    }

    @Bean
    fun limitOrderInputValidator(applicationSettingsHolder: ApplicationSettingsHolder): LimitOrderInputValidator {
        return LimitOrderInputValidatorImpl(applicationSettingsHolder)
    }

    @Bean
    fun orderBookMaxTotalSizeHolder(): OrderBookMaxTotalSizeHolder {
        return OrderBookMaxTotalSizeHolderImpl(null)
    }

    @Bean
    fun limitOrderBusinessValidator(orderBookMaxTotalSizeHolder: OrderBookMaxTotalSizeHolder): LimitOrderBusinessValidator {
        return LimitOrderBusinessValidatorImpl(orderBookMaxTotalSizeHolder)
    }

    @Bean
    fun stopOrderBusinessValidatorImpl(orderBookMaxTotalSizeHolder: OrderBookMaxTotalSizeHolder): StopOrderBusinessValidatorImpl {
        return StopOrderBusinessValidatorImpl(orderBookMaxTotalSizeHolder)
    }

    @Bean
    fun limitOrderCancelOperationInputValidator(): LimitOrderCancelOperationInputValidator {
        return LimitOrderCancelOperationInputValidatorImpl()
    }

    @Bean
    fun limitOrderCancelOperationBusinessValidator(): LimitOrderCancelOperationBusinessValidator {
        return LimitOrderCancelOperationBusinessValidatorImpl()
    }

    @Bean
    fun limitOrdersCancelServiceHelper(limitOrdersCancelExecutor: LimitOrdersCancelExecutor): LimitOrdersCancelServiceHelper {
        return LimitOrdersCancelServiceHelper(limitOrdersCancelExecutor)
    }

    @Bean
    fun limitOrderCancelService(genericLimitOrderService: GenericLimitOrderService,
                                     genericStopLimitOrderService: GenericStopLimitOrderService,
                                     validator: LimitOrderCancelOperationBusinessValidator,
                                     limitOrdersCancelServiceHelper: LimitOrdersCancelServiceHelper): LimitOrderCancelService {
        return LimitOrderCancelService(genericLimitOrderService, genericStopLimitOrderService, validator, limitOrdersCancelServiceHelper)
    }

    @Bean
    fun limitOrderCancelOperationContextParser(): LimitOrderCancelOperationContextParser {
        return LimitOrderCancelOperationContextParser()
    }

    @Bean
    fun limitOrderMassCancelOperationContextParser(): ContextParser<LimitOrderMassCancelOperationParsedData> {
        return LimitOrderMassCancelOperationContextParser()
    }

    @Bean
    fun limitOrderMassCancelService(genericLimitOrderService: GenericLimitOrderService,
                                         genericStopLimitOrderService: GenericStopLimitOrderService,
                                         limitOrdersCancelServiceHelper: LimitOrdersCancelServiceHelper): LimitOrderMassCancelService {
        return LimitOrderMassCancelService(genericLimitOrderService, genericStopLimitOrderService, limitOrdersCancelServiceHelper)
    }

    @Bean
    fun disabledFunctionalityRulesService(): DisabledFunctionalityRulesService {
        return DisabledFunctionalityRulesServiceImpl()
    }

    @Bean
    fun singleLimitOrderPreprocessor(singleLimitOrderContextParser: SingleLimitOrderContextParser,
                                          preProcessedMessageQueue: BlockingQueue<MessageWrapper>,
                                          messageProcessingStatusHolder: MessageProcessingStatusHolder): SingleLimitOrderPreprocessor {
        return SingleLimitOrderPreprocessor(singleLimitOrderContextParser,
                preProcessedMessageQueue,
                messageProcessingStatusHolder,
                ThrottlingLogger.getLogger("limitOrder"))
    }

    @Bean
    fun expiryOrdersQueue() = ExpiryOrdersQueue()

    @Bean
    fun messageRouter(limitOrderInputQueue: BlockingQueue<MessageWrapper>,
                           cashInOutInputQueue: BlockingQueue<MessageWrapper>,
                           cashTransferInputQueue: BlockingQueue<MessageWrapper>,
                           limitOrderCancelInputQueue: BlockingQueue<MessageWrapper>,
                           limitOrderMassCancelInputQueue: BlockingQueue<MessageWrapper>,
                           preProcessedMessageQueue: BlockingQueue<MessageWrapper>): MessageRouter {
        return MessageRouter(limitOrderInputQueue,
                cashInOutInputQueue,
                cashTransferInputQueue,
                limitOrderCancelInputQueue,
                limitOrderMassCancelInputQueue,
                preProcessedMessageQueue)
    }

    @Bean
    fun specializedEventSendersHolder(specializedEventSenders: List<SpecializedEventSender<*>>): SpecializedEventSendersHolder {
        return SpecializedEventSendersHolderImpl(specializedEventSenders)
    }
}