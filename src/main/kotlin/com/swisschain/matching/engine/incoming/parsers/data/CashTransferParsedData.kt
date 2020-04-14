package com.swisschain.matching.engine.incoming.parsers.data

import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.messages.MessageWrapper

class CashTransferParsedData(messageWrapper: MessageWrapper,
                             val assetId: String,
                             val feeInstructions: List<NewFeeInstruction>): ParsedData(messageWrapper)