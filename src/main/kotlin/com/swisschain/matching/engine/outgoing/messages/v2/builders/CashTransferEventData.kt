package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.daos.TransferOperation
import com.swisschain.matching.engine.daos.fee.v2.Fee
import com.swisschain.matching.engine.outgoing.messages.ClientBalanceUpdate

class CashTransferEventData(val clientBalanceUpdates: List<ClientBalanceUpdate>,
                            val transferOperation: TransferOperation,
                            val internalFees: List<Fee>): EventData