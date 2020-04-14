package com.swisschain.matching.engine.daos

import com.swisschain.matching.engine.order.SequenceNumbersWrapper
import com.swisschain.matching.engine.order.transaction.ExecutionContext

class ExecutionData(val executionContext: ExecutionContext,
                    val sequenceNumbers: SequenceNumbersWrapper): OutgoingEventData