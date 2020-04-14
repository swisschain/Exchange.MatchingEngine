package com.swisschain.matching.engine.config

import com.swisschain.matching.engine.daos.ExecutionData
import com.swisschain.matching.engine.daos.OutgoingEventData
import com.swisschain.matching.engine.database.PersistenceManager
import com.swisschain.matching.engine.fee.FeeProcessor
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.AssetsPairsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.holders.MessageSequenceNumberHolder
import com.swisschain.matching.engine.holders.UUIDHolder
import com.swisschain.matching.engine.matching.MatchingEngine
import com.swisschain.matching.engine.order.ExecutionDataApplyService
import com.swisschain.matching.engine.order.ExecutionPersistenceService
import com.swisschain.matching.engine.order.process.GenericLimitOrdersProcessor
import com.swisschain.matching.engine.order.process.LimitOrderProcessor
import com.swisschain.matching.engine.order.process.PreviousLimitOrdersProcessor
import com.swisschain.matching.engine.order.process.StopLimitOrderProcessor
import com.swisschain.matching.engine.order.process.StopOrderBookProcessor
import com.swisschain.matching.engine.order.process.common.LimitOrdersCancelExecutor
import com.swisschain.matching.engine.order.process.common.LimitOrdersCancelExecutorImpl
import com.swisschain.matching.engine.order.process.common.LimitOrdersCanceller
import com.swisschain.matching.engine.order.process.common.LimitOrdersCancellerImpl
import com.swisschain.matching.engine.order.process.common.MatchingResultHandlingHelper
import com.swisschain.matching.engine.order.transaction.ExecutionContextFactory
import com.swisschain.matching.engine.order.transaction.ExecutionEventsSequenceNumbersGenerator
import com.swisschain.matching.engine.outgoing.messages.CashInOutEventData
import com.swisschain.matching.engine.outgoing.messages.CashTransferEventData
import com.swisschain.matching.engine.outgoing.messages.ReservedCashInOutEventData
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import com.swisschain.matching.engine.outgoing.senders.OutgoingEventProcessor
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSender
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSendersHolder
import com.swisschain.matching.engine.outgoing.senders.impl.OutgoingEventProcessorImpl
import com.swisschain.matching.engine.outgoing.senders.impl.specialized.CashInOutEventSender
import com.swisschain.matching.engine.outgoing.senders.impl.specialized.CashTransferEventSender
import com.swisschain.matching.engine.outgoing.senders.impl.specialized.ExecutionEventSender
import com.swisschain.matching.engine.outgoing.senders.impl.specialized.ReservedCashInOutEventSender
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.services.GenericStopLimitOrderService
import com.swisschain.matching.engine.services.MessageSender
import com.swisschain.matching.engine.services.validators.business.LimitOrderBusinessValidator
import com.swisschain.matching.engine.services.validators.business.StopOrderBusinessValidator
import com.swisschain.matching.engine.services.validators.input.LimitOrderInputValidator
import com.swisschain.matching.engine.utils.SyncQueue
import org.mockito.Mock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import java.util.concurrent.BlockingQueue

@Configuration
open class TestExecutionContext {

    @Mock
    private val outgoingEvents = SyncQueue<OutgoingEventData>()

    @Bean
    open fun matchingEngine(genericLimitOrderService: GenericLimitOrderService,
                            feeProcessor: FeeProcessor,
                            uuidHolder: UUIDHolder): MatchingEngine {
        return MatchingEngine(genericLimitOrderService,
                feeProcessor,
                uuidHolder)
    }

    @Bean
    open fun executionContextFactory(balancesHolder: BalancesHolder,
                                     genericLimitOrderService: GenericLimitOrderService,
                                     genericStopLimitOrderService: GenericStopLimitOrderService,
                                     assetsHolder: AssetsHolder): ExecutionContextFactory {
        return ExecutionContextFactory(balancesHolder,
                genericLimitOrderService,
                genericStopLimitOrderService,
                assetsHolder)
    }

    @Bean
    open fun executionEventsSequenceNumbersGenerator(messageSequenceNumberHolder: MessageSequenceNumberHolder): ExecutionEventsSequenceNumbersGenerator {
        return ExecutionEventsSequenceNumbersGenerator(messageSequenceNumberHolder)
    }

    @Bean
    open fun executionPersistenceService(persistenceManager: PersistenceManager): ExecutionPersistenceService {
        return ExecutionPersistenceService(persistenceManager)
    }


    @Bean
    open fun outgoingEventProcessor(specializedEventSendersHolder: SpecializedEventSendersHolder,
                                    @Qualifier("outgoingPublishersThreadPool")
                                    outgoingPublishersThreadPool: TaskExecutor): OutgoingEventProcessor {
        return OutgoingEventProcessorImpl(outgoingEvents, specializedEventSendersHolder, outgoingPublishersThreadPool)
    }

    @Bean
    open fun cashTransferNewSender(messageSender: MessageSender): SpecializedEventSender<CashTransferEventData> {
        return CashTransferEventSender(messageSender)
    }

    @Bean
    open fun specializedExecutionEventSender(messageSender: MessageSender,
                                             genericLimitOrderService: GenericLimitOrderService,
                                             outgoingOrderBookQueue: BlockingQueue<OrderBookEvent>): SpecializedEventSender<ExecutionData> {
        return ExecutionEventSender(messageSender)
    }

    @Bean
    open fun specializedCashInOutEventSender(messageSender: MessageSender): SpecializedEventSender<CashInOutEventData> {
        return CashInOutEventSender(messageSender)
    }

    @Bean
    open fun specializedReservedCashInOutEventSender(messageSender: MessageSender): SpecializedEventSender<ReservedCashInOutEventData> {
        return ReservedCashInOutEventSender(messageSender)
    }

    @Bean
    open fun executionDataApplyService(executionEventsSequenceNumbersGenerator: ExecutionEventsSequenceNumbersGenerator,
                                       executionPersistenceService: ExecutionPersistenceService,
                                       outgoingEventProcessor: OutgoingEventProcessor): ExecutionDataApplyService {
        return ExecutionDataApplyService(executionEventsSequenceNumbersGenerator,
                executionPersistenceService,
                outgoingEventProcessor)
    }

    @Bean
    open fun limitOrderProcessor(limitOrderInputValidator: LimitOrderInputValidator,
                                 limitOrderBusinessValidator: LimitOrderBusinessValidator,
                                 applicationSettingsHolder: ApplicationSettingsHolder,
                                 matchingEngine: MatchingEngine,
                                 matchingResultHandlingHelper: MatchingResultHandlingHelper): LimitOrderProcessor {
        return LimitOrderProcessor(limitOrderInputValidator,
                limitOrderBusinessValidator,
                applicationSettingsHolder,
                matchingEngine,
                matchingResultHandlingHelper)
    }

    @Bean
    open fun stopLimitOrdersProcessor(limitOrderInputValidator: LimitOrderInputValidator,
                                      stopOrderBusinessValidator: StopOrderBusinessValidator,
                                      applicationSettingsHolder: ApplicationSettingsHolder,
                                      limitOrderProcessor: LimitOrderProcessor,
                                      uuidHolder: UUIDHolder): StopLimitOrderProcessor {
        return StopLimitOrderProcessor(limitOrderInputValidator,
                stopOrderBusinessValidator,
                applicationSettingsHolder,
                limitOrderProcessor,
                uuidHolder)
    }

    @Bean
    open fun genericLimitOrdersProcessor(limitOrderProcessor: LimitOrderProcessor,
                                         stopLimitOrdersProcessor: StopLimitOrderProcessor): GenericLimitOrdersProcessor {
        return GenericLimitOrdersProcessor(limitOrderProcessor, stopLimitOrdersProcessor)
    }

    @Bean
    open fun stopOrderBookProcessor(limitOrderProcessor: LimitOrderProcessor,
                                    applicationSettingsHolder: ApplicationSettingsHolder,
                                    uuidHolder: UUIDHolder): StopOrderBookProcessor {
        return StopOrderBookProcessor(limitOrderProcessor,
                applicationSettingsHolder,
                uuidHolder)
    }

    @Bean
    fun matchingResultHandlingHelper(applicationSettingsHolder: ApplicationSettingsHolder): MatchingResultHandlingHelper {
        return MatchingResultHandlingHelper(applicationSettingsHolder)
    }

    @Bean
    open fun previousLimitOrdersProcessor(genericLimitOrderService: GenericLimitOrderService,
                                          genericStopLimitOrderService: GenericStopLimitOrderService,
                                          limitOrdersCanceller: LimitOrdersCanceller): PreviousLimitOrdersProcessor {
        return PreviousLimitOrdersProcessor(genericLimitOrderService, genericStopLimitOrderService, limitOrdersCanceller)
    }

    @Bean
    open fun limitOrdersCanceller(applicationSettingsHolder: ApplicationSettingsHolder): LimitOrdersCanceller {
        return LimitOrdersCancellerImpl(applicationSettingsHolder)
    }

    @Bean
    open fun limitOrdersCancelExecutor(assetsPairsHolder: AssetsPairsHolder,
                                       executionContextFactory: ExecutionContextFactory,
                                       limitOrdersCanceller: LimitOrdersCanceller,
                                       stopOrderBookProcessor: StopOrderBookProcessor,
                                       executionDataApplyService: ExecutionDataApplyService): LimitOrdersCancelExecutor {
        return LimitOrdersCancelExecutorImpl(assetsPairsHolder,
                executionContextFactory,
                limitOrdersCanceller,
                stopOrderBookProcessor,
                executionDataApplyService)
    }
}