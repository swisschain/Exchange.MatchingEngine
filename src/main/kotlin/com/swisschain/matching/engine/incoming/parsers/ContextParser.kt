package com.swisschain.matching.engine.incoming.parsers

import com.swisschain.matching.engine.incoming.parsers.data.ParsedData
import com.swisschain.matching.engine.messages.MessageWrapper

interface ContextParser<out R: ParsedData> {
    fun parse(messageWrapper: MessageWrapper): R
}