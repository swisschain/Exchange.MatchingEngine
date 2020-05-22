package com.swisschain.matching.engine.web.dto

import java.math.BigDecimal

data class StopOrder(val id: String,
                     val walletId: Long,
                     val volume: BigDecimal,
                     val lowerLimitPrice: BigDecimal?,
                     val lowerPrice: BigDecimal?,
                     val upperLimitPrice: BigDecimal?,
                     val upperPrice: BigDecimal?)