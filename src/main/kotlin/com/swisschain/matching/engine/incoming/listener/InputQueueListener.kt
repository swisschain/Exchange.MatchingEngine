package com.swisschain.matching.engine.incoming.listener

import com.swisschain.matching.engine.incoming.preprocessor.MessagePreprocessor
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.utils.logging.MetricsLogger
import com.swisschain.utils.logging.ThrottlingLogger
import java.util.concurrent.BlockingQueue
import javax.annotation.PostConstruct

class InputQueueListener(private val inputQueue: BlockingQueue<MessageWrapper>,
                         private val preProcessor: MessagePreprocessor,
                         private val logger: ThrottlingLogger,
                         threadName: String) : Thread(threadName) {

    companion object {
        private val METRICS_LOGGER = MetricsLogger.getLogger()
        private const val ERROR_MESSAGE = "Unable to pre process message"
    }

    @PostConstruct
    fun init() = start()

    override fun run() {
        while (true) {
            try {
                preProcessor.preProcess(inputQueue.take())
            } catch (e: Exception) {
                logger.error(ERROR_MESSAGE, e)
                METRICS_LOGGER.logError(ERROR_MESSAGE, e)
            }
        }
    }
}