package com.swisschain.matching.engine.database.common.entity

import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.daos.wallet.Wallet

class BalancesData(
        val wallets: Collection<Wallet>,
        val balances: Collection<AssetBalance>
)