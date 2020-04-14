package com.swisschain.matching.engine.outgoing.messages.v2.builders

import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.outgoing.messages.ClientBalanceUpdate

class ReservedBalanceUpdateData(val clientBalanceUpdates: List<ClientBalanceUpdate>,
                                val reservedBalanceUpdateOperation: WalletOperation): EventData