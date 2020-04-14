package com.swisschain.matching.engine.incoming.preprocessor.impl

import com.swisschain.matching.engine.AbstractTest
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.order.LimitOrderType
import com.swisschain.matching.engine.grpc.TestStreamObserver
import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.utils.MessageBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SingleLimitOrderPreprocessorTest: AbstractTest() {

    @Autowired
    private lateinit var singleLimitOrderPreprocessor: SingleLimitOrderPreprocessor

    @Autowired
    private lateinit var messageBuilder: MessageBuilder

    @Test
    fun testOrderWithUnknownAssetPair() {
        val messageWrapper = messageBuilder.buildLimitOrderWrapper(MessageBuilder.buildLimitOrder(assetId = "UnknownAssetPair"))
        singleLimitOrderPreprocessor.preProcess(messageWrapper)

        val clientHandler = messageWrapper.callback as TestStreamObserver
        assertEquals(1, clientHandler.responses.size)

        val response = clientHandler.responses.single()
        assertEquals(MessageStatus.UNKNOWN_ASSET.type, response.statusValue)

        assertEquals(0, clientsEventsQueue.size)
    }

    @Test
    fun testStopOrderWithUnknownAssetPair() {
        val messageWrapper = messageBuilder.buildLimitOrderWrapper(MessageBuilder.buildLimitOrder(assetId = "UnknownAssetPair",
                type = LimitOrderType.STOP_LIMIT, lowerPrice = 1.0, lowerLimitPrice = 1.0))
        singleLimitOrderPreprocessor.preProcess(messageWrapper)

        val clientHandler = messageWrapper.callback as TestStreamObserver
        assertEquals(1, clientHandler.responses.size)

        val response = clientHandler.responses.single()
        assertEquals(MessageStatus.UNKNOWN_ASSET.type, response.statusValue)

        assertEquals(0, clientsEventsQueue.size)
    }
}