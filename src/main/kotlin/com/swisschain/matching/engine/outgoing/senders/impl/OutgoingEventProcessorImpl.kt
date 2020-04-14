package com.swisschain.matching.engine.outgoing.senders.impl

import com.swisschain.matching.engine.daos.ExecutionData
import com.swisschain.matching.engine.daos.OutgoingEventData
import com.swisschain.matching.engine.outgoing.messages.CashInOutEventData
import com.swisschain.matching.engine.outgoing.messages.CashTransferEventData
import com.swisschain.matching.engine.outgoing.messages.ReservedCashInOutEventData
import com.swisschain.matching.engine.outgoing.senders.OutgoingEventProcessor
import com.swisschain.matching.engine.outgoing.senders.SpecializedEventSendersHolder
import com.swisschain.utils.logging.ThrottlingLogger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component
import org.springframework.util.CollectionUtils
import java.util.concurrent.BlockingQueue
import javax.annotation.PostConstruct

@Component
class OutgoingEventProcessorImpl(private val outgoingEventDataQueue: BlockingQueue<OutgoingEventData>,
                                 private val specializedEventSendersHolder: SpecializedEventSendersHolder,
                                 @Qualifier("outgoingPublishersThreadPool")
                                 private val outgoingPublishersThreadPool: TaskExecutor): OutgoingEventProcessor {

    private companion object {
        val LOGGER = ThrottlingLogger.getLogger(OutgoingEventProcessorImpl::class.java.name)
    }

    @PostConstruct
    private fun init() {
        outgoingPublishersThreadPool.execute {
            Thread.currentThread().name = OutgoingEventProcessorImpl::class.java.simpleName
            while (true) {
                try {
                    processEvent(outgoingEventDataQueue.take())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
            }
        }
    }

    override fun submitCashTransferEvent(cashTransferEventData: CashTransferEventData) {
        submitEvent(cashTransferEventData)
    }

    override fun submitCashInOutEvent(cashInOutEventData: CashInOutEventData) {
        submitEvent(cashInOutEventData)
    }

    override fun submitExecutionEvent(executionEventData: ExecutionData) {
        submitEvent(executionEventData)
    }

    override fun submitReservedCashInOutEvent(reservedCashInOutEventData: ReservedCashInOutEventData) {
        submitEvent(reservedCashInOutEventData)
    }

    private fun submitEvent(outgoingEventData: OutgoingEventData) {
        outgoingEventDataQueue.put(outgoingEventData)
    }

    private fun processEvent(eventData: OutgoingEventData) {
        val eventSenders = specializedEventSendersHolder.getSenders(eventData)
        if (CollectionUtils.isEmpty(eventSenders)) {
            LOGGER.warn("Sender for class: ${eventData::class.java.name}, was not found, event is ignored")
        }
        eventSenders.forEach {
            it.sendEvent(eventData)
        }
    }
}