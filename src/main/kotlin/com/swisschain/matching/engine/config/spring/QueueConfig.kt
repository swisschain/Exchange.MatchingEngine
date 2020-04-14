package com.swisschain.matching.engine.config.spring

import com.swisschain.matching.engine.daos.OutgoingEventData
import com.swisschain.matching.engine.daos.TransferOperation
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.swisschain.matching.engine.outgoing.messages.v2.events.OrderBookEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.BlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue
import com.swisschain.matching.engine.logging.MessageWrapper as LoggingMessageWrapper

@Configuration
open class QueueConfig {

    //<editor-fold desc="Outgoing queues">
    @Bean
    @OutgoingQueue
    open fun clientsEventsQueue(): BlockingDeque<Event> {
        return LinkedBlockingDeque()
    }

    @Bean
    @OutgoingQueue
    open fun trustedClientsEventsQueue(): BlockingDeque<ExecutionEvent> {
        return LinkedBlockingDeque()
    }

    @Bean
    @OutgoingQueue
    open fun outgoingOrderBookQueue(): BlockingDeque<OrderBookEvent> {
        return LinkedBlockingDeque<OrderBookEvent>()
    }

    @Bean
    @OutgoingQueue
    open fun outgoingEventDataQueue(): BlockingQueue<OutgoingEventData> {
        return LinkedBlockingQueue<OutgoingEventData>()
    }
    //</editor-fold>


    //<editor-fold desc="Input queues">
    @Bean
    @InputQueue
    open fun limitOrderInputQueue(): BlockingQueue<MessageWrapper> {
        return LinkedBlockingQueue<MessageWrapper>()
    }

    @Bean
    @InputQueue
    open fun cashInOutInputQueue(): BlockingQueue<MessageWrapper> {
        return LinkedBlockingQueue<MessageWrapper>()
    }

    @Bean
    @InputQueue
    open fun cashTransferInputQueue(): BlockingQueue<MessageWrapper> {
        return LinkedBlockingQueue<MessageWrapper>()
    }

    @Bean
    @InputQueue
    open fun limitOrderCancelInputQueue(): BlockingQueue<MessageWrapper>{
        return LinkedBlockingQueue<MessageWrapper>()
    }

    @Bean
    @InputQueue
    open fun limitOrderMassCancelInputQueue(): BlockingQueue<MessageWrapper>{
        return LinkedBlockingQueue<MessageWrapper>()
    }

    @Bean
    @InputQueue
    open fun preProcessedMessageQueue(): BlockingQueue<MessageWrapper> {
        return LinkedBlockingQueue<MessageWrapper>()
    }
    //</editor-fold>

    //<editor-fold desc="Data queues">
    @Bean
    @DataQueue
    open fun balanceUpdatesLogQueue(): BlockingQueue<LoggingMessageWrapper> {
        return LinkedBlockingQueue()
    }

    @Bean
    @DataQueue
    open fun cashInOutLogQueue(): BlockingQueue<LoggingMessageWrapper> {
        return LinkedBlockingQueue()
    }

    @Bean
    @DataQueue
    open fun cashTransferLogQueue(): BlockingQueue<LoggingMessageWrapper> {
        return LinkedBlockingQueue()
    }

    @Bean
    @DataQueue
    open fun clientLimitOrdersLogQueue(): BlockingQueue<LoggingMessageWrapper> {
        return LinkedBlockingQueue()
    }

    @Bean
    @DataQueue
    open fun marketOrderWithTradesLogQueue(): BlockingQueue<LoggingMessageWrapper> {
        return LinkedBlockingQueue()
    }

    @Bean
    @DataQueue
    open fun reservedCashOperationLogQueue(): BlockingQueue<LoggingMessageWrapper> {
        return LinkedBlockingQueue()
    }

    @Bean
    @DataQueue
    open fun dbTransferOperationQueue(): BlockingQueue<TransferOperation> {
        return LinkedBlockingQueue<TransferOperation>()
    }
    //</editor-fold>
}