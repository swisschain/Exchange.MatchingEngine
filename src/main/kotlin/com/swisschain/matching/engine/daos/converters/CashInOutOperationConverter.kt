package com.swisschain.matching.engine.daos.converters

import com.swisschain.matching.engine.daos.CashInOutOperation
import com.swisschain.matching.engine.daos.WalletOperation
import java.math.BigDecimal

class CashInOutOperationConverter {
    companion object {
        fun fromCashInOutOperationToWalletOperation(cashInOutOperation: CashInOutOperation): WalletOperation {
            return WalletOperation(cashInOutOperation.brokerId,
                    cashInOutOperation.walletId,
                    cashInOutOperation.asset!!.symbol,
                    cashInOutOperation.amount,
                    BigDecimal.ZERO,
                    cashInOutOperation.description)
        }
    }
}