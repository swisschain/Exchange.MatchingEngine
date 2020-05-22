package com.swisschain.matching.engine.outgoing.messages

import java.util.Date

class ReservedCashOperation(val id: String,
                            val walletId: Long,
                            val dateTime: Date,
                            val reservedVolume: String,
                            var asset: String,
                            val messageId: String)