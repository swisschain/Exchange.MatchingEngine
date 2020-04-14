package com.swisschain.matching.engine.config.spring

import com.swisschain.matching.engine.database.grpc.GrpcMessageLogDatabaseAccessor
import com.swisschain.matching.engine.logging.DatabaseLogger
import com.swisschain.matching.engine.logging.MessageWrapper
import com.swisschain.matching.engine.outgoing.messages.BalanceUpdate
import com.swisschain.matching.engine.outgoing.messages.CashOperation
import com.swisschain.matching.engine.outgoing.messages.CashTransferOperation
import com.swisschain.matching.engine.outgoing.messages.LimitOrdersReport
import com.swisschain.matching.engine.outgoing.messages.MarketOrderWithTrades
import com.swisschain.matching.engine.outgoing.messages.ReservedCashOperation
import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.utils.AppInitializer
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.util.concurrent.BlockingQueue
import javax.annotation.PostConstruct

@Component
open class LoggerConfig {

    @Autowired
    private lateinit var config: Config

    @Bean(destroyMethod = "")
    open fun appStarterLogger(): Logger {
        return LoggerFactory.getLogger("AppStarter")
    }

    @Bean
    open fun singleLimitOrderPreProcessingLogger(): ThrottlingLogger {
        return ThrottlingLogger.getLogger("SingleLimitOrderPreProcessing")
    }

    @Bean
    open fun cashInOutPreProcessingLogger(): ThrottlingLogger {
        return ThrottlingLogger.getLogger("CashInOutPreProcessing")
    }

    @Bean
    open fun cashTransferPreProcessingLogger(): ThrottlingLogger {
        return ThrottlingLogger.getLogger("CashTransferPreProcessing")
    }

    @Bean
    open fun limitOrderCancelPreProcessingLogger(): ThrottlingLogger {
        return ThrottlingLogger.getLogger("LimitOrderCancelPreProcessing")
    }

    @Bean
    open fun limitOrderMassCancelPreProcessingLogger(): ThrottlingLogger {
        return ThrottlingLogger.getLogger("LimitOrderMassCancelPreProcessing")
    }

    @Bean
    open fun balanceUpdatesDatabaseLogger(balanceUpdatesLogQueue: BlockingQueue<MessageWrapper>): DatabaseLogger<*> {
        return DatabaseLogger<BalanceUpdate>(GrpcMessageLogDatabaseAccessor(config.me.grpcEndpoints.messageLogServiceConnection), balanceUpdatesLogQueue)
    }

    @Bean
    open fun cashInOutDatabaseLogger(cashInOutLogQueue: BlockingQueue<MessageWrapper>): DatabaseLogger<*> {
        return DatabaseLogger<CashOperation>(GrpcMessageLogDatabaseAccessor(config.me.grpcEndpoints.messageLogServiceConnection), cashInOutLogQueue)
    }

    @Bean
    open fun cashTransferDatabaseLogger(cashTransferLogQueue: BlockingQueue<MessageWrapper>): DatabaseLogger<*> {
        return DatabaseLogger<CashTransferOperation>(GrpcMessageLogDatabaseAccessor(config.me.grpcEndpoints.messageLogServiceConnection), cashTransferLogQueue)
    }

    @Bean
    open fun clientLimitOrderDatabaseLogger(clientLimitOrdersLogQueue: BlockingQueue<MessageWrapper>): DatabaseLogger<*> {
        return DatabaseLogger<LimitOrdersReport>(GrpcMessageLogDatabaseAccessor(config.me.grpcEndpoints.messageLogServiceConnection), clientLimitOrdersLogQueue)
    }

    @Bean
    open fun marketOrderWithTradesDatabaseLogger(marketOrderWithTradesLogQueue: BlockingQueue<MessageWrapper>): DatabaseLogger<*> {
        return DatabaseLogger<MarketOrderWithTrades>(GrpcMessageLogDatabaseAccessor(config.me.grpcEndpoints.messageLogServiceConnection), marketOrderWithTradesLogQueue)
    }

    @Bean
    open fun reservedCashOperationDatabaseLogger(reservedCashOperationLogQueue: BlockingQueue<MessageWrapper>): DatabaseLogger<*> {
        return DatabaseLogger<ReservedCashOperation>(GrpcMessageLogDatabaseAccessor(config.me.grpcEndpoints.messageLogServiceConnection), reservedCashOperationLogQueue)
    }

    @PostConstruct
    open fun init() {
        AppInitializer.init()
        MetricsLogger.init("ME", config.slackNotifications)
        ThrottlingLogger.init(config.throttlingLogger)
    }
}