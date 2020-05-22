package com.swisschain.matching.engine.services.validators.business.impl

import com.swisschain.matching.engine.daos.context.CashInOutContext
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.services.validators.business.CashInOutOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.utils.NumberUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component("CashInOutOperationBusinessValidator")
class CashInOutOperationBusinessValidatorImpl(private val balancesHolder: BalancesHolder) : CashInOutOperationBusinessValidator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CashInOutOperationBusinessValidatorImpl::class.java.name)
    }

    override fun performValidation(cashInOutContext: CashInOutContext) {
        isBalanceValid(cashInOutContext)
    }

    private fun isBalanceValid(cashInOutContext: CashInOutContext) {
        val amount = cashInOutContext.cashInOutOperation.amount
        if (amount < BigDecimal.ZERO) {
            val asset = cashInOutContext.cashInOutOperation.asset!!
            val balance = balancesHolder.getBalance(cashInOutContext.cashInOutOperation.brokerId, cashInOutContext.cashInOutOperation.walletId, asset.symbol)
            val reservedBalance = balancesHolder.getReservedBalance(cashInOutContext.cashInOutOperation.brokerId, cashInOutContext.cashInOutOperation.accountId, cashInOutContext.cashInOutOperation.walletId, asset.symbol)
            if (NumberUtils.setScaleRoundHalfUp(balance - reservedBalance + amount, asset.accuracy) < BigDecimal.ZERO) {
                LOGGER.info("Cash out operation (${cashInOutContext.cashInOutOperation.externalId}) " +
                        "for client ${cashInOutContext.cashInOutOperation.walletId} asset ${asset.symbol}, " +
                        "volume: ${NumberUtils.roundForPrint(amount)}: low balance $balance, " +
                        "reserved balance $reservedBalance")
                throw ValidationException(ValidationException.Validation.LOW_BALANCE)
            }
        }

    }
}