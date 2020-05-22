package com.swisschain.matching.engine.daos

import java.math.BigDecimal

class FeeTransfer(val fromAccountId: Long,
                  val fromWalletId: Long,
                  val toAccountId: Long,
                  val toWalletId: Long,
                  val volume: BigDecimal,
                  val asset: String,
                  val feeCoef: BigDecimal?)