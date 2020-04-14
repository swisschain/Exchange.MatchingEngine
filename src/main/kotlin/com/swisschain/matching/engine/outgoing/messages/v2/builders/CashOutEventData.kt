package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.daos.fee.v2.Fee
import com.swisschain.matching.engine.outgoing.messages.ClientBalanceUpdate

class CashOutEventData(val clientBalanceUpdates: List<ClientBalanceUpdate>,
                       val cashOutOperation: WalletOperation,
                       val internalFees: List<Fee>): EventData