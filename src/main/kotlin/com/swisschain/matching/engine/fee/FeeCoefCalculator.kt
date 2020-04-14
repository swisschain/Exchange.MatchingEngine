package com.swisschain.matching.engine.fee

import java.math.BigDecimal

interface FeeCoefCalculator {
    fun calculate(): BigDecimal?
}