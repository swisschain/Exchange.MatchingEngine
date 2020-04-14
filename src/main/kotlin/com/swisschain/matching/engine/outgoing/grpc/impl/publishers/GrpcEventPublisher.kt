package com.swisschain.matching.engine.outgoing.grpc.impl.publishers

import com.swisschain.matching.engine.logging.DatabaseLogger
import com.swisschain.matching.engine.messages.outgoing.GrpOutgoingEventsServiceGrpc
import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import com.swisschain.matching.engine.outgoing.messages.v2.events.Event
import com.swisschain.matching.engine.outgoing.publishers.events.PublisherFailureEvent
import com.swisschain.matching.engine.outgoing.publishers.events.PublisherReadyEvent
import com.swisschain.matching.engine.utils.NumberUtils
import com.swisschain.matching.engine.utils.PrintUtils
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.BlockingQueue

class GrpcEventPublisher(
        private val publisherName: String,
        private val queue: BlockingQueue<out Event>,
        private val queueName: String,
        private val grpcConnectionString: String,
        private val applicationEventPublisher: ApplicationEventPublisher,
        /** null if do not need to log */
        private val messageDatabaseLogger: DatabaseLogger<Event>? = null): Runnable {

    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(GrpcEventPublisher::class.java.name)
        private val MESSAGES_LOGGER = LoggerFactory.getLogger("${GrpcEventPublisher::class.java.name}.message")
        private val METRICS_LOGGER = MetricsLogger.getLogger()
        private val STATS_LOGGER = LoggerFactory.getLogger("${GrpcEventPublisher::class.java.name}.stats")

        private const val LOG_COUNT = 1000
        private const val RECONNECTION_INTERVAL = 1000L
    }

    private var messagesCount: Long = 0
    private var totalPersistTime: Double = 0.0
    private var totalTime: Double = 0.0

    @Volatile
    private var currentlyPublishedItem: Event? = null

    private var channel: ManagedChannel? = null
    private var grpcStub: GrpOutgoingEventsServiceGrpc.GrpOutgoingEventsServiceBlockingStub? = null

    private fun publish(message: Event) {
        var isLogged = false
        while (true) {
            try {
                val startTime = System.nanoTime()

                if (!isLogged) {
                    logMessage(message)
                    isLogged = true
                }

                val startPersistTime = System.nanoTime()
                val result = grpcStub!!.publishEvents(message.buildGeneratedMessage() as OutgoingMessages.MessageWrapper)
                if (result.published) {
                    val endPersistTime = System.nanoTime()
                    val endTime = System.nanoTime()
                    fixTime(startTime, endTime, startPersistTime, endPersistTime)

                    return
                }
            } catch (exception: Exception) {
                publishFailureEvent(message)
                val logMessage = "Exception during GRPC publishing ($grpcConnectionString): ${exception.message}"
                LOGGER.error(logMessage, exception)
                METRICS_LOGGER.logError(logMessage, exception)
                tryConnectUntilSuccess()
            }
        }
    }

    override fun run() {
        Thread.currentThread().name = "GrpcEventPublisher_$publisherName"
        tryConnectUntilSuccess()
        while (true) {
            val item = queue.take()
            currentlyPublishedItem = item
            publish(item)
        }
    }

    private fun tryConnectUntilSuccess() {
        while (!connect()) {
            Thread.sleep(RECONNECTION_INTERVAL)
        }
    }

    private fun connect(): Boolean {
        return try {
            channel = ManagedChannelBuilder.forTarget(grpcConnectionString).usePlaintext().build()
            grpcStub = GrpOutgoingEventsServiceGrpc.newBlockingStub(channel)
            publishReadyEvent()
            true
        } catch (e: Exception) {
            LOGGER.error("GrpcPublisher $publisherName failed to connect", e)
            publishFailureEvent(null)
            false
        }
    }


    private fun fixTime(startTime: Long, endTime: Long, startPersistTime: Long, endPersistTime: Long) {
        messagesCount++
        totalPersistTime += (endPersistTime - startPersistTime).toDouble() / LOG_COUNT
        totalTime += (endTime - startTime).toDouble() / LOG_COUNT

        if (messagesCount % LOG_COUNT == 0L) {
            STATS_LOGGER.info("gRPC: $grpcConnectionString. Messages: ${LOG_COUNT}. Total: ${PrintUtils.convertToString(totalTime)}. " +
                    " Persist: ${PrintUtils.convertToString(totalPersistTime)}, ${NumberUtils.roundForPrint2(100 * totalPersistTime / totalTime)} %")
            totalPersistTime = 0.0
            totalTime = 0.0
        }
    }

    private fun publishReadyEvent() {
        applicationEventPublisher.publishEvent(PublisherReadyEvent(queueName))
    }

    private fun publishFailureEvent(event: Event?) {
        applicationEventPublisher.publishEvent(PublisherFailureEvent(queueName, event))
    }

    private fun logMessage(item: Event) {
        if (messageDatabaseLogger != null) {
            MESSAGES_LOGGER.info("$publisherName : ${item.buildGeneratedMessage().toString()}")
            messageDatabaseLogger.log(item)
        }
    }
}