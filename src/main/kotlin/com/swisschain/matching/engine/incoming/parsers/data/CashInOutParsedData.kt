package com.swisschain.matching.engine.incoming.parsers.data

import com.swisschain.matching.engine.messages.MessageWrapper

class CashInOutParsedData(messageWrapper: MessageWrapper,
                          val assetId: String): ParsedData(messageWrapper)