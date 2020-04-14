package com.swisschain.matching.engine.outgoing.messages

import com.swisschain.matching.engine.daos.FeeTransfer
import com.swisschain.matching.engine.daos.fee.v2.Fee
import com.swisschain.matching.engine.daos.v2.FeeInstruction
import java.math.BigDecimal
import java.util.Date

class CashTransferOperation(
        val id: String,
        val fromWalletId: String,
        val toWalletId: String,
        val dateTime: Date,
        val volume: String,
        val overdraftLimit: BigDecimal?,
        var asset: String,
        val feeInstruction: FeeInstruction?,
        val feeTransfer: FeeTransfer?,
        val fees: List<Fee>?,
        val messageId: String)