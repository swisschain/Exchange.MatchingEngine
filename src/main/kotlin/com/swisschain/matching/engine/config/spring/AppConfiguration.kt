package com.swisschain.matching.engine.config.spring

import com.swisschain.matching.engine.AppInitialData
import com.swisschain.matching.engine.database.stub.StubAliveStatusDatabaseAccessor
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSender
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSendersHolder
import com.swisschain.matching.engine.outgoing.senders.impl.SpecializedEventSendersHolderImpl
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.services.GenericStopLimitOrderService
import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.matching.engine.utils.monitoring.MonitoredComponent
import com.swisschain.matching.engine.utils.monitoring.MonitoringStatsCollector
import com.swisschain.matching.engine.utils.monitoring.QueueSizeHealthChecker
import com.swisschain.utils.alivestatus.processor.AliveStatusProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.BlockingQueue

@Configuration
open class AppConfiguration {

    @Autowired
    private lateinit var config: Config


    @Bean
    open fun appInitData(genericLimitOrderService: GenericLimitOrderService,
                         genericStopLimitOrderService: GenericStopLimitOrderService,
                         balanceHolder: BalancesHolder): AppInitialData {
        return AppInitialData(genericLimitOrderService.initialOrdersCount, genericStopLimitOrderService.initialStopOrdersCount,
                balanceHolder.initialBalancesCount, balanceHolder.initialClientsCount)
    }

    @Bean
    open fun grpcStatusProcessor(): Runnable {
        return AliveStatusProcessor(
                StubAliveStatusDatabaseAccessor(), config.me.aliveStatus)
    }

    @Bean
    open fun inputQueueSizeChecker(@InputQueue namesToInputQueues: Map<String, BlockingQueue<*>>): QueueSizeHealthChecker {
        return QueueSizeHealthChecker(
                MonitoredComponent.INPUT_QUEUE,
                namesToInputQueues,
                config.me.queueConfig.maxQueueSizeLimit,
                config.me.queueConfig.recoverQueueSizeLimit)
    }

    @Bean
    open fun outgoingQueueSizeChecker(@OutgoingQueue namesToInputQueues: Map<String, BlockingQueue<*>>): QueueSizeHealthChecker {
        return QueueSizeHealthChecker(
                MonitoredComponent.OUTGOING_QUEUE,
                namesToInputQueues,
                config.me.queueConfig.outgoingMaxQueueSizeLimit,
                config.me.queueConfig.outgoingRecoverQueueSizeLimit)
    }

    @Bean
    open fun specializedEventSendersHolder(specializedEventSenders: List<SpecializedEventSender<*>>): SpecializedEventSendersHolder {
        return SpecializedEventSendersHolderImpl(specializedEventSenders)
    }

    @Bean
    open fun dataQueueSizeChecker(@DataQueue namesToInputQueues: Map<String, BlockingQueue<*>>): QueueSizeHealthChecker {
        return QueueSizeHealthChecker(
                MonitoredComponent.DATA_QUEUE,
                namesToInputQueues,
                config.me.queueConfig.dataMaxQueueSizeLimit,
                config.me.queueConfig.dataRecoverQueueSizeLimit)
    }

    @Bean
    open fun monitoringStatsCollector(): MonitoringStatsCollector {
        return MonitoringStatsCollector()
    }
}

