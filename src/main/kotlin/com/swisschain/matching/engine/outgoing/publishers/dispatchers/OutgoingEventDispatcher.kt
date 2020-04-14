package com.swisschain.matching.engine.outgoing.publishers.dispatchers

import com.swisschain.matching.engine.outgoing.publishers.events.PublisherFailureEvent
import com.swisschain.matching.engine.outgoing.publishers.events.PublisherReadyEvent
import com.swisschain.matching.engine.utils.monitoring.HealthMonitorEvent
import com.swisschain.matching.engine.utils.monitoring.MonitoredComponent
import com.swisschain.utils.logging.MetricsLogger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import java.util.concurrent.BlockingDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.PostConstruct

class OutgoingEventDispatcher<E>(private val dispatcherName: String,
                                 private val inputDeque: BlockingDeque<E>,
                                 private val queueNameToQueue: Map<String, BlockingQueue<E>>) : Thread(dispatcherName) {

    companion object {
        val METRICS_LOGGER = MetricsLogger.getLogger()
        val LOGGER = LoggerFactory.getLogger(OutgoingEventDispatcher::class.java)
    }

    private var failedEventConsumers = HashSet<String>()

    private var maintenanceModeLock = ReentrantLock()
    private var maintenanceModeCondition = maintenanceModeLock.newCondition()

    @Autowired
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    override fun run() {
        try {
            while (true) {
                val event = inputDeque.take()
                dispatchEventToEventListeners(event)
            }
        } catch (e: Exception) {
            logException("Error occurred in events dispatcher thread for dispatcher: $dispatcherName", e)
        }
    }

    @PostConstruct
    private fun init() {
        val queueNames = queueNameToQueue.keys.joinToString()
        LOGGER.info("Starting publishers dispatcher for queues: $queueNames")
        this.start()
    }

    private fun dispatchEventToEventListeners(event: E) {
        try {
            maintenanceModeLock.lock()

            while (failedEventConsumers.size == queueNameToQueue.size) {
                maintenanceModeCondition.await()
            }

            queueNameToQueue.keys.forEach {
                dispatchEventToEventListener(event, it)
            }
        } finally {
            maintenanceModeLock.unlock()
        }
    }

    private fun dispatchEventToEventListener(event: E, listenerName: String) {
        try {
            if (failedEventConsumers.contains(listenerName)) {
                return
            }
            queueNameToQueue[listenerName]?.put(event)
        } catch (e: Exception) {
            //normally never occur, exist to be sure event is dispatched to the rest of listeners
            logException("Failed to dispatch event, in dispatcher: $dispatcherName, for listener: $listenerName", e)
        }
    }

    @EventListener
    private fun onPublisherFailure(publisherFailureEvent: PublisherFailureEvent<E>) {
        if (!queueNameToQueue.keys.contains(publisherFailureEvent.publisherName)) {
            return
        }

        try {
            maintenanceModeLock.lock()
            failedEventConsumers.add(publisherFailureEvent.publisherName)

            logError("Publisher ${publisherFailureEvent.publisherName} crashed, count of functional publishers is ${queueNameToQueue.size - failedEventConsumers.size}")

            val failedConsumerQueue = queueNameToQueue[publisherFailureEvent.publisherName]

            failedConsumerQueue?.reversed()?.forEach {
                inputDeque.putFirst(it)
            }

            publisherFailureEvent.failedEvent?.let { inputDeque.putFirst(it) }

            if (queueNameToQueue.size == failedEventConsumers.size) {
                logError("Publishers crashed, dispatcher: $dispatcherName")
                applicationEventPublisher.publishEvent(HealthMonitorEvent(false, MonitoredComponent.PUBLISHER, dispatcherName))
            }

            failedConsumerQueue?.clear()
        } catch (e: Exception) {
            logException("Error occurred on dispatcher failure recording in dispatcher: $dispatcherName", e)
        } finally {
            maintenanceModeLock.unlock()
        }
    }

    @EventListener
    private fun onPublisherReady(publisherReadyEvent: PublisherReadyEvent) {
        if (!queueNameToQueue.keys.contains(publisherReadyEvent.publisherName)) {
            return
        }

        try {
            maintenanceModeLock.lock()
            if (failedEventConsumers.remove(publisherReadyEvent.publisherName)) {
                log("Publisher recovered: ${publisherReadyEvent.publisherName}, count of functional publishers is ${queueNameToQueue.size - failedEventConsumers.size}")
            }
            maintenanceModeCondition.signal()

            applicationEventPublisher.publishEvent(HealthMonitorEvent(true, MonitoredComponent.PUBLISHER, dispatcherName))
        } catch (e: Exception) {
            logException("Error occurred on dispatcher recovery from maintenance mode for exchange: $dispatcherName", e)
        } finally {
            maintenanceModeLock.unlock()
        }
    }

    private fun logException(message: String, e: Exception) {
        METRICS_LOGGER.logError(message, e)
        LOGGER.error(message, e)
    }

    private fun logError(message: String) {
        METRICS_LOGGER.logError(message)
        LOGGER.error(message)
    }

    private fun log(message: String){
        METRICS_LOGGER.logWarning(message)
        LOGGER.info(message)
    }
}