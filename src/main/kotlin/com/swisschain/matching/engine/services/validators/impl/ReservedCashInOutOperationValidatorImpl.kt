package com.swisschain.matching.engine.services.validators.impl

import com.swisschain.matching.engine.holders.AssetsHolder
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.messages.incoming.IncomingMessages
import com.swisschain.matching.engine.services.validators.ReservedCashInOutOperationValidator
import com.swisschain.matching.engine.utils.NumberUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ReservedCashInOutOperationValidatorImpl @Autowired constructor(private val assetsHolder: AssetsHolder,
                                                                     private val balancesHolder: BalancesHolder,
                                                                     @Value("#{Config.me.defaultBroker}" )
                                                                     private val defaultBrokerId: String) : ReservedCashInOutOperationValidator {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ReservedCashInOutOperationValidatorImpl::class.java.name)
    }

    override fun performValidation(message: IncomingMessages.ReservedCashInOutOperation) {
        val brokerId = if (!message.brokerId.isNullOrEmpty()) message.brokerId else defaultBrokerId
        isVolumeAccuracyValid(brokerId, message)

        if (BigDecimal(message.reservedVolume) < BigDecimal.ZERO) {
            isBalanceValid(brokerId, message)
        } else {
            isReservedVolumeValid(brokerId, message)
        }
    }

    private fun isReservedVolumeValid(brokerId: String, message: IncomingMessages.ReservedCashInOutOperation) {
        val accuracy = assetsHolder.getAsset(brokerId, message.assetId).accuracy
        val reservedBalance = balancesHolder.getReservedBalance(brokerId, message.walletId, message.assetId)

        val balance = balancesHolder.getBalance(brokerId, message.walletId, message.assetId)
        if (NumberUtils.setScaleRoundHalfUp(balance - reservedBalance - BigDecimal(message.reservedVolume), accuracy) < BigDecimal.ZERO) {
            LOGGER.info("Reserved cash in operation (${message.id}) for client ${message.walletId} asset ${message.assetId}, " +
                    "volume: ${message.reservedVolume}: low balance $balance, " +
                    "current reserved balance $reservedBalance")
            throw ValidationException(ValidationException.Validation.RESERVED_VOLUME_HIGHER_THAN_BALANCE)
        }
    }

    private fun isBalanceValid(brokerId: String, message: IncomingMessages.ReservedCashInOutOperation) {
        val accuracy = assetsHolder.getAsset(brokerId, message.assetId).accuracy
        val reservedBalance = balancesHolder.getReservedBalance(brokerId, message.walletId, message.assetId)

        if (NumberUtils.setScaleRoundHalfUp(reservedBalance + BigDecimal(message.reservedVolume), accuracy) < BigDecimal.ZERO) {
            LOGGER.info("Reserved cash out operation (${message.id}) for client ${message.walletId} asset ${message.assetId}, " +
                    "volume: ${message.reservedVolume}: low reserved balance $reservedBalance")
            throw ValidationException(ValidationException.Validation.LOW_BALANCE)
        }
    }

    private fun isVolumeAccuracyValid(brokerId: String, message: IncomingMessages.ReservedCashInOutOperation) {
        val assetId = message.assetId
        val volume = message.reservedVolume
        val volumeValid = NumberUtils.isScaleSmallerOrEqual(BigDecimal(volume), assetsHolder.getAsset(brokerId, assetId).accuracy)

        if (!volumeValid) {
            LOGGER.info("Volume accuracy invalid, assetId $assetId, volume $volume")
            throw ValidationException(ValidationException.Validation.INVALID_VOLUME_ACCURACY)
        }
    }
}