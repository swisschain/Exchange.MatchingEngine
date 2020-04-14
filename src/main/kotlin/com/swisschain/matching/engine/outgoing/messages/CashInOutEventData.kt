package com.swisschain.matching.engine.outgoing.messages

import com.swisschain.matching.engine.balance.WalletOperationsProcessor
import com.swisschain.matching.engine.daos.Asset
import com.swisschain.matching.engine.daos.OutgoingEventData
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.daos.fee.v2.Fee
import java.util.Date

class CashInOutEventData(val messageId: String,
                         val externalId: String,
                         val sequenceNumber: Long,
                         val now: Date,
                         val timestamp: Date,
                         val walletProcessor: WalletOperationsProcessor,
                         val walletOperation: WalletOperation,
                         val asset: Asset,
                         val internalFees: List<Fee>
): OutgoingEventData