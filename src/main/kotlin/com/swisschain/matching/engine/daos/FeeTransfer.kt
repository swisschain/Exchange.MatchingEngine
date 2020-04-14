package com.swisschain.matching.engine.daos

import java.math.BigDecimal

class FeeTransfer(val fromWalletId: String,
                  val toWalletId: String,
                  val volume: BigDecimal,
                  val asset: String,
                  val feeCoef: BigDecimal?)