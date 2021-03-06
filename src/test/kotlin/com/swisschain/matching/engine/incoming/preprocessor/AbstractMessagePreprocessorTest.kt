package com.swisschain.matching.engine.incoming.preprocessor

import com.swisschain.matching.engine.grpc.TestStreamObserver
import com.swisschain.matching.engine.holders.MessageProcessingStatusHolder
import com.swisschain.matching.engine.incoming.parsers.ContextParser
import com.swisschain.matching.engine.incoming.parsers.data.ParsedData
import com.swisschain.matching.engine.messages.GenericMessageWrapper
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageWrapper
import com.swisschain.utils.logging.ThrottlingLogger
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AbstractMessagePreprocessorTest {

    class TestParsedData(messageWrapper: MessageWrapper) : ParsedData(messageWrapper)

    class TestMessagePreprocessor(contextParser: ContextParser<TestParsedData>,
                                  messageProcessingStatusHolder: MessageProcessingStatusHolder,
                                  preProcessedMessageQueue: BlockingQueue<MessageWrapper>,
                                  private val preProcessSuccess: Boolean) :
            AbstractMessagePreprocessor<TestParsedData>(contextParser,
                    messageProcessingStatusHolder,
                    preProcessedMessageQueue,
                    ThrottlingLogger.getLogger(TestMessagePreprocessor::class.java.name)) {

        override fun preProcessParsedData(parsedData: TestParsedData): Boolean {
            return preProcessSuccess
        }

    }

    private lateinit var queue: BlockingQueue<MessageWrapper>
    private lateinit var clientHandler: TestStreamObserver
    private lateinit var messageWrapper: GenericMessageWrapper

    @Before
    fun setUp() {
        queue = LinkedBlockingQueue<MessageWrapper>()
        clientHandler = TestStreamObserver()
        messageWrapper = GenericMessageWrapper(1, null, clientHandler, false, id = "id", messageId = "messageId")
    }

    private fun createStatusHolder(isMessageProcessingEnabled: Boolean, isHealthStatusOk: Boolean): MessageProcessingStatusHolder {
        val statusHolder = Mockito.mock(MessageProcessingStatusHolder::class.java)
        Mockito.`when`(statusHolder.isMessageProcessingEnabled())
                .thenReturn(isMessageProcessingEnabled)
        Mockito.`when`(statusHolder.isHealthStatusOk())
                .thenReturn(isHealthStatusOk)
        return statusHolder
    }

    private fun createPreprocessor(isMessageProcessingEnabled: Boolean,
                                   isHealthStatusOk: Boolean,
                                   preProcessSuccess: Boolean = true): TestMessagePreprocessor {
        @Suppress("UNCHECKED_CAST")
        val contextParser = Mockito.mock(ContextParser::class.java) { TestParsedData(messageWrapper) }
                as ContextParser<TestParsedData>
        return TestMessagePreprocessor(contextParser,
                createStatusHolder(isMessageProcessingEnabled, isHealthStatusOk),
                queue,
                preProcessSuccess)
    }

    @Test
    fun testPreProcess() {
        val messagePreprocessor = createPreprocessor(true, true)

        messagePreprocessor.preProcess(messageWrapper)

        assertNotNull(messageWrapper.messagePreProcessorStartTimestamp)
        assertNotNull(messageWrapper.messagePreProcessorEndTimestamp)
        assertEquals(0, clientHandler.responses.size)
        assertEquals(1, queue.size)
        assertEquals(messageWrapper, queue.poll())
    }

    @Test
    fun testPreProcessWithMaintenanceMode() {
        val messagePreprocessor = createPreprocessor(true, false)

        messagePreprocessor.preProcess(messageWrapper)

        assertNotNull(messageWrapper.messagePreProcessorStartTimestamp)
        assertNotNull(messageWrapper.messagePreProcessorEndTimestamp)
        assertEquals(1, clientHandler.responses.size)
        val response = clientHandler.responses.single()
        assertEquals(MessageStatus.RUNTIME.type, response.statusValue)

        assertEquals(0, queue.size)
    }

    @Test
    fun testPreProcessWithDisabledMessageProcessing() {
        val messagePreprocessor = createPreprocessor(false, true)

        messagePreprocessor.preProcess(messageWrapper)

        assertNotNull(messageWrapper.messagePreProcessorStartTimestamp)
        assertNotNull(messageWrapper.messagePreProcessorEndTimestamp)
        assertEquals(1, clientHandler.responses.size)
        val response = clientHandler.responses.single()
        assertEquals(MessageStatus.MESSAGE_PROCESSING_DISABLED.type, response.statusValue)

        assertEquals(0, queue.size)
    }

    @Test
    fun testPreProcessWithDisabledMessageProcessingAndMaintenanceMode() {
        val messagePreprocessor = createPreprocessor(false, false)

        messagePreprocessor.preProcess(messageWrapper)

        assertNotNull(messageWrapper.messagePreProcessorStartTimestamp)
        assertNotNull(messageWrapper.messagePreProcessorEndTimestamp)
        assertEquals(1, clientHandler.responses.size)
        val response = clientHandler.responses.single()
        assertEquals(MessageStatus.MESSAGE_PROCESSING_DISABLED.type, response.statusValue)

        assertEquals(0, queue.size)
    }

    @Test
    fun testFailPreProcess() {
        val messagePreprocessor = createPreprocessor(true, true, false)

        messagePreprocessor.preProcess(messageWrapper)

        assertNotNull(messageWrapper.messagePreProcessorStartTimestamp)
        assertNotNull(messageWrapper.messagePreProcessorEndTimestamp)
        assertEquals(0, clientHandler.responses.size)
        assertEquals(0, queue.size)
    }
}
