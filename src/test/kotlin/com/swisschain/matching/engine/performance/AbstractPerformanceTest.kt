package com.swisschain.matching.engine.performance

import com.swisschain.matching.engine.balance.util.TestBalanceHolderWrapper
import com.swisschain.matching.engine.daos.OutgoingEventData
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.database.TestDictionariesDatabaseAccessor
import com.swisschain.matching.engine.database.TestFileOrderDatabaseAccessor
import com.swisschain.matching.engine.database.TestFileStopOrderDatabaseAccessor
import com.swisschain.matching.engine.database.TestMessageSequenceNumberDatabaseAccessor
import com.swisschain.matching.engine.database.TestOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.TestPersistenceManager
import com.swisschain.matching.engine.database.TestSettingsDatabaseAccessor
import com.swisschain.matching.engine.database.TestStopOrderBookDatabaseAccessor
import com.swisschain.matching.engine.database.TestWalletDatabaseAccessor
import com.swisschain.matching.engine.database.cache.ApplicationSettingsCache
import com.swisschain.matching.engine.database.cache.AssetPairsCache
import com.swisschain.matching.engine.database.cache.AssetsCache
import com.swisschain.matching.engine.fee.FeeProcessor
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.holders.OrderBookMaxTotalSizeHolderImpl
import com.swisschain.matching.engine.holders.OrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.holders.StopOrdersDatabaseAccessorsHolder
import com.swisschain.matching.engine.holders.TestUUIDHolder
import com.swisschain.matching.engine.incoming.parsers.impl.CashInOutContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.CashTransferContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.LimitOrderCancelOperationContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.LimitOrderMassCancelOperationContextParser
import com.swisschain.matching.engine.incoming.parsers.impl.SingleLimitOrderContextParser
import com.swisschain.matching.engine.matching.MatchingEngine
import com.swisschain.matching.engine.order.ExecutionDataApplyService
import com.swisschain.matching.engine.order.ExecutionPersistenceService
import com.swisschain.matching.engine.order.ExpiryOrdersQueue
import com.swisschain.matching.engine.order.process.GenericLimitOrdersProcessor
import com.swisschain.matching.engine.order.process.LimitOrderProcessor
import com.swisschain.matching.engine.order.process.PreviousLimitOrdersProcessor
import com.swisschain.matching.engine.order.process.StopLimitOrderProcessor
import com.swisschain.matching.engine.order.process.StopOrderBookProcessor
import com.swisschain.matching.engine.order.process.common.LimitOrdersCancellerImpl
import com.swisschain.matching.engine.order.process.common.MatchingResultHandlingHelper
import com.swisschain.matching.engine.order.transaction.ExecutionContextFactory
import com.swisschain.matching.engine.order.transaction.ExecutionEventsSequenceNumbersGenerator
import com.swisschain.matching.engine.outgoing.messages.LimitOrdersReport
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import com.swisschain.matching.engine.outgoing.senders.impl.OutgoingEventProcessorImpl
import com.swisschain.matching.engine.outgoing.senders.impl.SpecializedEventSendersHolderImpl
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.services.GenericStopLimitOrderService
import com.swisschain.matching.engine.services.MarketOrderService
import com.swisschain.matching.engine.services.MessageSender
import com.swisschain.matching.engine.services.MultiLimitOrderService
import com.swisschain.matching.engine.services.SingleLimitOrderService
import com.swisschain.matching.engine.services.validators.business.impl.LimitOrderBusinessValidatorImpl
import com.swisschain.matching.engine.services.validators.business.impl.StopOrderBusinessValidatorImpl
import com.swisschain.matching.engine.services.validators.impl.MarketOrderValidatorImpl
import com.swisschain.matching.engine.services.validators.input.impl.LimitOrderInputValidatorImpl
import com.swisschain.matching.engine.utils.MessageBuilder
import com.swisschain.utils.logging.ThrottlingLogger
import org.mockito.Mockito
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.task.TaskExecutor
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

abstract class AbstractPerformanceTest {

    companion object {
        val REPEAT_TIMES = 100
        private val LOGGER = ThrottlingLogger.getLogger(AbstractPerformanceTest::class.java.name)
    }

    protected val testDictionariesDatabaseAccessor = TestDictionariesDatabaseAccessor()
    protected lateinit var testSettingsDatabaseAccessor: TestSettingsDatabaseAccessor

    protected lateinit var singleLimitOrderService: SingleLimitOrderService
    protected lateinit var genericStopLimitOrderService: GenericStopLimitOrderService
    protected lateinit var genericLimitOrderService: GenericLimitOrderService
    protected lateinit var multiLimitOrderService: MultiLimitOrderService
    protected lateinit var marketOrderService: MarketOrderService

    protected lateinit var assetsHolder: AssetsHolder
    protected lateinit var balancesHolder: BalancesHolder
    protected lateinit var assetsPairsHolder: AssetsPairsHolder
    protected lateinit var assetCache: AssetsCache
    protected lateinit var testWalletDatabaseAccessor: TestWalletDatabaseAccessor

    protected val secondaryOrdersDatabaseAccessor = TestFileOrderDatabaseAccessor()
    protected val primaryOrdersDatabaseAccessor = TestOrderBookDatabaseAccessor(secondaryOrdersDatabaseAccessor)
    private var ordersDatabaseAccessorsHolder = OrdersDatabaseAccessorsHolder(primaryOrdersDatabaseAccessor, secondaryOrdersDatabaseAccessor)

    protected val secondaryStopOrdersDatabaseAccessor = TestFileStopOrderDatabaseAccessor()
    protected val primaryStopOrdersDatabaseAccessor = TestStopOrderBookDatabaseAccessor(secondaryStopOrdersDatabaseAccessor)

    private var stopOrdersDatabaseAccessorsHolder = StopOrdersDatabaseAccessorsHolder(primaryStopOrdersDatabaseAccessor, secondaryStopOrdersDatabaseAccessor)
    private var messageProcessingStatusHolder = Mockito.mock(MessageProcessingStatusHolder::class.java)

    protected lateinit var assetPairsCache: AssetPairsCache
    protected lateinit var applicationSettingsHolder: ApplicationSettingsHolder
    protected lateinit var applicationSettingsCache: ApplicationSettingsCache
    protected lateinit var persistenceManager: PersistenceManager

    protected var outgoingEventsQueue = LinkedBlockingQueue<Event>()
    protected var outgoingTrustedClientsEventsQueue = LinkedBlockingQueue<ExecutionEvent>()

    protected lateinit var singleLimitOrderContextParser: SingleLimitOrderContextParser
    protected lateinit var cashInOutContextParser: CashInOutContextParser
    protected lateinit var cashTransferContextParser: CashTransferContextParser

    protected lateinit var testBalanceHolderWrapper: TestBalanceHolderWrapper

    private lateinit var feeProcessor: FeeProcessor
    private lateinit var expiryOrdersQueue: ExpiryOrdersQueue

    protected lateinit var messageBuilder: MessageBuilder

    val orderBookQueue = LinkedBlockingQueue<OrderBookEvent>()

    val trustedClientsLimitOrdersQueue = LinkedBlockingQueue<LimitOrdersReport>()

    val outgoingEventData = LinkedBlockingQueue<OutgoingEventData>()

    val messageSender = MessageSender(outgoingEventsQueue, outgoingTrustedClientsEventsQueue, orderBookQueue)

    private fun clearMessageQueues() {
        outgoingEventsQueue.clear()
        outgoingTrustedClientsEventsQueue.clear()
        orderBookQueue.clear()
        trustedClientsLimitOrdersQueue.clear()
    }

    open fun initServices() {
        val uuidHolder = TestUUIDHolder()
        clearMessageQueues()
        testSettingsDatabaseAccessor = TestSettingsDatabaseAccessor()
        applicationSettingsCache = ApplicationSettingsCache(testSettingsDatabaseAccessor, ApplicationEventPublisher {})
        applicationSettingsHolder = ApplicationSettingsHolder(applicationSettingsCache)
        val limitOrdersCanceller = LimitOrdersCancellerImpl(applicationSettingsHolder)

        assetCache = AssetsCache(testDictionariesDatabaseAccessor)
        assetsHolder = AssetsHolder(assetCache)
        testWalletDatabaseAccessor = TestWalletDatabaseAccessor()
        persistenceManager = TestPersistenceManager(testWalletDatabaseAccessor,
                ordersDatabaseAccessorsHolder,
                stopOrdersDatabaseAccessorsHolder)
        balancesHolder = BalancesHolder(testWalletDatabaseAccessor,
                persistenceManager,
                assetsHolder,
                applicationSettingsHolder)

        testBalanceHolderWrapper = TestBalanceHolderWrapper(balancesHolder)
        assetPairsCache = AssetPairsCache(testDictionariesDatabaseAccessor)
        assetsPairsHolder = AssetsPairsHolder(assetPairsCache)

        expiryOrdersQueue = ExpiryOrdersQueue()
        genericLimitOrderService = GenericLimitOrderService(ordersDatabaseAccessorsHolder, expiryOrdersQueue)

        feeProcessor = FeeProcessor(assetsHolder, assetsPairsHolder, genericLimitOrderService)

        val messageSequenceNumberHolder = MessageSequenceNumberHolder(TestMessageSequenceNumberDatabaseAccessor())
        val limitOrderInputValidator = LimitOrderInputValidatorImpl(applicationSettingsHolder)
        singleLimitOrderContextParser = SingleLimitOrderContextParser(assetsPairsHolder,
                assetsHolder,
                applicationSettingsHolder,
                uuidHolder,
                LOGGER)
        cashInOutContextParser = CashInOutContextParser(assetsHolder, uuidHolder)
        cashTransferContextParser = CashTransferContextParser(assetsHolder, uuidHolder)

        messageBuilder = MessageBuilder(singleLimitOrderContextParser,
                cashInOutContextParser,
                cashTransferContextParser,
                LimitOrderCancelOperationContextParser(),
                LimitOrderMassCancelOperationContextParser())

        genericStopLimitOrderService = GenericStopLimitOrderService(stopOrdersDatabaseAccessorsHolder, expiryOrdersQueue)

        val executionEventsSequenceNumbersGenerator = ExecutionEventsSequenceNumbersGenerator(messageSequenceNumberHolder)
        val executionPersistenceService = ExecutionPersistenceService(persistenceManager)
        val outgoingEventProcessor = OutgoingEventProcessorImpl(
                outgoingEventData,
                SpecializedEventSendersHolderImpl(emptyList()),
                TaskExecutor { task -> thread(name = "outgoingMessageProcessor") { task.run() } })


        val executionDataApplyService = ExecutionDataApplyService(executionEventsSequenceNumbersGenerator,
                executionPersistenceService,
                outgoingEventProcessor)

        val executionContextFactory = ExecutionContextFactory(balancesHolder,
                genericLimitOrderService,
                genericStopLimitOrderService,
                assetsHolder)

        val matchingResultHandlingHelper = MatchingResultHandlingHelper(applicationSettingsHolder)

        val matchingEngine = MatchingEngine(genericLimitOrderService, feeProcessor, uuidHolder)

        val limitOrderProcessor = LimitOrderProcessor(limitOrderInputValidator,
                LimitOrderBusinessValidatorImpl(OrderBookMaxTotalSizeHolderImpl(null)),
                applicationSettingsHolder,
                matchingEngine,
                matchingResultHandlingHelper)

        val stopOrderProcessor = StopLimitOrderProcessor(limitOrderInputValidator,
                StopOrderBusinessValidatorImpl(OrderBookMaxTotalSizeHolderImpl(null)),
                applicationSettingsHolder,
                limitOrderProcessor,
                uuidHolder)

        val genericLimitOrdersProcessor = GenericLimitOrdersProcessor(limitOrderProcessor, stopOrderProcessor)

        val stopOrderBookProcessor = StopOrderBookProcessor(limitOrderProcessor, applicationSettingsHolder, uuidHolder)

        val previousLimitOrdersProcessor = PreviousLimitOrdersProcessor(genericLimitOrderService, genericStopLimitOrderService, limitOrdersCanceller)

        singleLimitOrderService = SingleLimitOrderService(executionContextFactory,
                genericLimitOrdersProcessor,
                stopOrderBookProcessor,
                executionDataApplyService,
                previousLimitOrdersProcessor)

        multiLimitOrderService = MultiLimitOrderService(executionContextFactory,
                genericLimitOrdersProcessor,
                stopOrderBookProcessor,
                executionDataApplyService,
                previousLimitOrdersProcessor,
                assetsHolder,
                assetsPairsHolder,
                balancesHolder,
                applicationSettingsHolder,
                messageProcessingStatusHolder,
                uuidHolder)

        val marketOrderValidator = MarketOrderValidatorImpl(assetsPairsHolder, assetsHolder, applicationSettingsHolder)
        marketOrderService = MarketOrderService(matchingEngine,
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

        startEventProcessorThread(outgoingEventData, "OutgoingEventData")
        startEventProcessorThread(outgoingEventsQueue, "ExecutionEventProcessor")
        startEventProcessorThread(outgoingTrustedClientsEventsQueue, "TrustedExecutionEventProcessor")
    }

    private fun startEventProcessorThread(queue: BlockingQueue<*>, name: String) {
        thread(name = name) {
            while (true) {
                queue.take()
            }
        }
    }
}
