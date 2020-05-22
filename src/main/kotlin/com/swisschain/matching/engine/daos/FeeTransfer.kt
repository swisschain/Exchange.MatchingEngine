package com.swisschain.matching.engine.daos

import java.math.BigDecimal

class FeeTransfer(val fromWalletId: Long,
                  val toWalletId: Long,
                  val volume: BigDecimal,
                  val asset: String,
                  val feeCoef: BigDecimal?)