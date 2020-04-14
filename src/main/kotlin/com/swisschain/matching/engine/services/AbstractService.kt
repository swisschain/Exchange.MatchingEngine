package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.messages.MessageStatus
import com.swisschain.matching.engine.messages.MessageWrapper

interface AbstractService {
    fun processMessage(messageWrapper: MessageWrapper)
    fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus)
}