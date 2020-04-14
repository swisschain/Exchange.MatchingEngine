package com.swisschain.matching.engine.daos.wallet

import java.io.Serializable
import java.math.BigDecimal

class AssetBalance(val brokerId: String,
                   val walletId: String,
                   val asset: String,
                   var balance: BigDecimal = BigDecimal.ZERO,
                   var reserved: BigDecimal = BigDecimal.ZERO) : Serializable