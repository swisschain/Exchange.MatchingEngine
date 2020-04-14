package com.swisschain.matching.engine.outgoing.messages

import com.swisschain.matching.engine.balance.WalletOperationsProcessor
import com.swisschain.matching.engine.daos.OutgoingEventData
import com.swisschain.matching.engine.daos.TransferOperation
import com.swisschain.matching.engine.daos.fee.v2.Fee
import java.util.Date

class CashTransferEventData(val messageId: String,
                            val walletProcessor: WalletOperationsProcessor,
                            val fees: List<Fee>,
                            val transferOperation: TransferOperation,
                            val sequenceNumber: Long,
                            val now: Date): OutgoingEventData