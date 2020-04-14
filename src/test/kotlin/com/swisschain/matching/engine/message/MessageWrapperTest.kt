package com.swisschain.matching.engine.message

//@RunWith(MockitoJUnitRunner::class)
class MessageWrapperTest {
    //TODO rewrite tests
//    @Mock
//    private lateinit var  streamObserver : StreamObserver<>
//
//    companion object {
//        val MESSAGE_ID1 = "messageID1"
//        val MESSAGE_ID2 = "messageID2"
//        val UID1 = 1L
//        val UID2 = 2L
//        val STATUS = MessageStatus.BALANCE_LOWER_THAN_RESERVED.type
//        val ASSET = "ASSET"
//    }
//
//    @Test
//    fun testWriteResponse() {
//        //given
//        val response = IncomingMessages.Response.newBuilder()
//                .setStatus(STATUS)
//        val responseMessage = IncomingMessages.Response.newBuilder()
//                .setMessageId(StringValue.of(MESSAGE_ID1))
//                .setId(StringValue.of(UID1.toString()))
//                .setStatus(STATUS)
//                .build()
//
//        val messageWrapper = MessageWrapper("test",
//                MessageType.RESPONSE.type,
//                ByteArray(0),
//                streamObserver,
//                System.currentTimeMillis(), messageId = MESSAGE_ID1, id = UID1.toString())
//
//        //when
//        messageWrapper.writeResponse(response)
//
//        //then
//        verify(streamObserver).writeOutput(eq(toByteArray(MessageType.RESPONSE.type, responseMessage.serializedSize, responseMessage.toByteArray())))
//    }
//
//    @Test
//    fun testWriteMarketOrderResponse() {
//        //given
//        val response = IncomingMessages.MarketOrderResponse.newBuilder()
//                .setId(StringValue.of(UID1.toString()))
//                .setStatus(STATUS)
//        val responseMessage = IncomingMessages.MarketOrderResponse.newBuilder()
//                .setId(StringValue.of(UID1.toString()))
//                .setMessageId(StringValue.of(MESSAGE_ID1))
//                .setStatus(STATUS)
//                .build()
//
//        val messageWrapper = MessageWrapper("test",
//                MessageType.RESPONSE.type,
//                ByteArray(0),
//                streamObserver,
//                System.currentTimeMillis(), messageId = MESSAGE_ID1, id = UID1.toString())
//
//        //when
//        messageWrapper.writeMarketOrderResponse(response)
//
//        //then
//        verify(streamObserver).writeOutput(eq(toByteArray(MessageType.MARKER_ORDER_RESPONSE.type, responseMessage.serializedSize, responseMessage.toByteArray())))
//    }
//
//    @Test
//    fun testWriteMultiLimitOrderResponse() {
//        //given
//        val response = IncomingMessages.MultiLimitOrderResponse.newBuilder()
//                .setStatus(STATUS)
//                .setAssetPairId(StringValue.of(ASSET))
//        val responseMessage = IncomingMessages.MultiLimitOrderResponse.newBuilder()
//                .setMessageId(StringValue.of(MESSAGE_ID1))
//                .setId(StringValue.of(UID1.toString()))
//                .setStatus(STATUS)
//                .setAssetPairId(StringValue.of(ASSET))
//                .build()
//
//        val messageWrapper = MessageWrapper("test",
//                MessageType.RESPONSE.type,
//                ByteArray(0),
//                streamObserver,
//                System.currentTimeMillis(), messageId = MESSAGE_ID1, id = UID1.toString())
//
//        //when
//        messageWrapper.writeMultiLimitOrderResponse(response)
//
//        //then
//        verify(streamObserver).writeOutput(eq(toByteArray(MessageType.MULTI_LIMIT_ORDER_RESPONSE.type, responseMessage.serializedSize, responseMessage.toByteArray())))
//    }
//
//    @Test
//    fun testWriteResponseIsNorErasedByMessageWrapper() {
//        //given
//        val response = IncomingMessages.Response.newBuilder()
//                .setStatus(STATUS)
//                .setId(StringValue.of(UID2.toString()))
//                .setMessageId(StringValue.of(MESSAGE_ID2))
//        val responseMessage = IncomingMessages.Response.newBuilder()
//                .setMessageId(StringValue.of(MESSAGE_ID2))
//                .setId(StringValue.of(UID2.toString()))
//                .setStatus(STATUS)
//                .build()
//
//        val messageWrapper = MessageWrapper("test",
//                MessageType.RESPONSE.type,
//                ByteArray(0),
//                streamObserver,
//                System.currentTimeMillis(), messageId = MESSAGE_ID1, id = UID1.toString())
//
//        //when
//        messageWrapper.writeResponse(response)
//
//        //then
//        verify(streamObserver).writeOutput(eq(toByteArray(MessageType.RESPONSE.type, responseMessage.serializedSize, responseMessage.toByteArray())))
//    }
//
//    @Test
//    fun testWriteMarketOrderResponseIsNorErasedByMessageWrapper() {
//        //given
//        val response = IncomingMessages.MarketOrderResponse.newBuilder()
//                .setId(StringValue.of(UID2.toString()))
//                .setMessageId(StringValue.of(MESSAGE_ID2))
//                .setStatus(STATUS)
//        val responseMessage = IncomingMessages.MarketOrderResponse.newBuilder()
//                .setId(StringValue.of(UID2.toString()))
//                .setMessageId(StringValue.of(MESSAGE_ID2))
//                .setStatus(STATUS)
//                .build()
//
//        val messageWrapper = MessageWrapper("test",
//                MessageType.RESPONSE.type,
//                ByteArray(0),
//                streamObserver,
//                System.currentTimeMillis(), messageId = MESSAGE_ID1, id = UID1.toString())
//
//        //when
//        messageWrapper.writeMarketOrderResponse(response)
//
//        //then
//        verify(streamObserver).writeOutput(eq(toByteArray(MessageType.MARKER_ORDER_RESPONSE.type, responseMessage.serializedSize, responseMessage.toByteArray())))
//    }
//
//    @Test
//    fun testWriteMultiLimitOrderResponseIsNorErasedByMessageWrapper() {
//        //given
//        val response = IncomingMessages.MultiLimitOrderResponse.newBuilder()
//                .setStatus(STATUS)
//                .setAssetPairId(StringValue.of(ASSET))
//                .setMessageId(StringValue.of(MESSAGE_ID2))
//                .setId(StringValue.of(UID2.toString()))
//        val responseMessage = IncomingMessages.MultiLimitOrderResponse.newBuilder()
//                .setMessageId(StringValue.of(MESSAGE_ID2))
//                .setId(StringValue.of(UID2.toString()))
//                .setStatus(STATUS)
//                .setAssetPairId(StringValue.of(ASSET))
//                .build()
//
//        val messageWrapper = MessageWrapper("test",
//                MessageType.RESPONSE.type,
//                ByteArray(0),
//                streamObserver,
//                System.currentTimeMillis(), messageId = MESSAGE_ID1, id = UID1.toString())
//
//        //when
//        messageWrapper.writeMultiLimitOrderResponse(response)
//
//        //then
//        verify(streamObserver).writeOutput(eq(toByteArray(MessageType.MULTI_LIMIT_ORDER_RESPONSE.type, responseMessage.serializedSize, responseMessage.toByteArray())))
//    }
}