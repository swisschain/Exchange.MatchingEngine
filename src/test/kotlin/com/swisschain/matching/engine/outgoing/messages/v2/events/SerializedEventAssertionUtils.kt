package com.swisschain.matching.engine.outgoing.messages.v2.events

import com.swisschain.matching.engine.messages.outgoing.OutgoingMessages
import kotlin.test.assertEquals

fun assertBalanceUpdate(walletId: Long,
                        assetId: String,
                        oldBalance: String,
                        newBalance: String,
                        oldReserved: String,
                        newReserved: String,
                        balanceUpdates: Collection<OutgoingMessages.BalanceUpdate>) {
    val balanceUpdate = balanceUpdates.single { it.walletId == walletId && it.assetId == assetId }
    assertEquals(oldBalance, balanceUpdate.oldBalance)
    assertEquals(newBalance, balanceUpdate.newBalance)
    assertEquals(oldReserved, balanceUpdate.oldReserved)
    assertEquals(newReserved, balanceUpdate.newReserved)
}