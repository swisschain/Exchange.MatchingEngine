package com.swisschain.matching.engine.services.validators.business.impl

import com.swisschain.matching.engine.daos.context.CashTransferContext
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.services.validators.business.CashTransferOperationBusinessValidator
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.utils.NumberUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class CashTransferOperationBusinessValidatorImpl (private val balancesHolder: BalancesHolder): CashTransferOperationBusinessValidator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CashTransferOperationBusinessValidatorImpl::class.java.name)

    }

    override fun performValidation(cashTransferContext: CashTransferContext) {
        validateBalanceValid(cashTransferContext)
        validateOverdraftLimitPositive(cashTransferContext)
    }

    private fun validateOverdraftLimitPositive(cashTransferContext: CashTransferContext) {
        val transferOperation = cashTransferContext.transferOperation
        val overdraftLimit = cashTransferContext.transferOperation.overdraftLimit

        if (overdraftLimit != null && overdraftLimit.signum() == -1) {
            throw ValidationException(ValidationException.Validation.NEGATIVE_OVERDRAFT_LIMIT, "WalletId:${transferOperation.fromWalletId}, " +
                    "asset:${transferOperation.asset}, volume:${transferOperation.volume}")
        }
    }

    private fun validateBalanceValid(cashTransferContext: CashTransferContext) {
        val transferOperation = cashTransferContext.transferOperation
        val asset = transferOperation.asset
        val balanceOfFromClient = balancesHolder.getBalance(transferOperation.brokerId, transferOperation.fromWalletId, asset.assetId)
        val reservedBalanceOfFromClient = balancesHolder.getReservedBalance(transferOperation.brokerId, transferOperation.fromWalletId, asset.assetId)
        val overdraftLimit = if (transferOperation.overdraftLimit != null) -transferOperation.overdraftLimit else BigDecimal.ZERO
        if (balanceOfFromClient - reservedBalanceOfFromClient - transferOperation.volume < overdraftLimit) {
            LOGGER.info("Cash transfer operation (${transferOperation.externalId}) from client ${transferOperation.fromWalletId} " +
                    "to client ${transferOperation.toWalletId}, asset ${transferOperation.asset}, " +
                    "volume: ${NumberUtils.roundForPrint(transferOperation.volume)}: " +
                    "low balance for client ${transferOperation.fromWalletId}")

            throw ValidationException(ValidationException.Validation.LOW_BALANCE, "WalletId:${transferOperation.fromWalletId}, " +
                    "asset:${transferOperation.asset}, volume:${transferOperation.volume}")
        }
    }
}