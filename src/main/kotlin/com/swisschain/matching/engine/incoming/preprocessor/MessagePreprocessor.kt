package com.swisschain.matching.engine.incoming.preprocessor

import com.swisschain.matching.engine.messages.MessageWrapper

interface MessagePreprocessor {
    fun preProcess(messageWrapper: MessageWrapper)
}