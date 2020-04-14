package com.swisschain.matching.engine.incoming.parsers.data

import com.swisschain.matching.engine.messages.MessageWrapper

class SingleLimitOrderParsedData(messageWrapper: MessageWrapper, val inputAssetPairId: String): ParsedData(messageWrapper)