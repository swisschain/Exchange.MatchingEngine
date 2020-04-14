package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.daos.fee.v2.Fee
import com.swisschain.matching.engine.outgoing.messages.ClientBalanceUpdate

class CashInEventData(val clientBalanceUpdates: List<ClientBalanceUpdate>,
                      val cashInOperation: WalletOperation,
                      val internalFees: List<Fee>): EventData