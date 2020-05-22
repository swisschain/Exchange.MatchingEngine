package com.swisschain.matching.engine.outgoing.messages

import com.swisschain.matching.engine.daos.fee.v2.Fee
import java.util.Date

class CashOperation(val id: String,
                    val walletId: Long,
                    val dateTime: Date,
                    val volume: String,
                    val asset: String,
                    val messageId: String,
                    val fees: List<Fee>?)