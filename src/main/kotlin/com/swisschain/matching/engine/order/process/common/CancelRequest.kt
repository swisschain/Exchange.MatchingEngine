package com.swisschain.matching.engine.order.process.common

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.deduplication.ProcessedMessage
import com.swisschain.matching.engine.messages.MessageType
import com.swisschain.matching.engine.messages.MessageWrapper
import org.slf4j.Logger
import java.util.Date

class CancelRequest(val limitOrders: Collection<LimitOrder>,
                    val stopLimitOrders: Collection<LimitOrder>,
                    val messageId: String,
                    val requestId: String,
                    val messageType: MessageType,
                    val date: Date,
                    val processedMessage: ProcessedMessage?,
                    val messageWrapper: MessageWrapper?,
                    val logger: Logger)