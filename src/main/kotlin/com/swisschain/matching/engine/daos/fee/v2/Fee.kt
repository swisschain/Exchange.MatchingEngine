package com.swisschain.matching.engine.daos.fee.v2

import com.swisschain.matching.engine.daos.FeeTransfer
import com.swisschain.matching.engine.daos.v2.FeeInstruction

data class Fee(val instruction: FeeInstruction,
               val transfer: FeeTransfer?)