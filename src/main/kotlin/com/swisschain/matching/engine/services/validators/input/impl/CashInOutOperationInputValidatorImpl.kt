package com.swisschain.matching.engine.services.validators.input.impl

import com.swisschain.matching.engine.daos.context.CashInOutContext
import com.swisschain.matching.engine.daos.fee.v2.NewFeeInstruction
import com.swisschain.matching.engine.fee.checkFee
import com.swisschain.matching.engine.holders.ApplicationSettingsHolder
import com.swisschain.matching.engine.incoming.parsers.data.CashInOutParsedData
import com.swisschain.matching.engine.services.validators.impl.ValidationException
import com.swisschain.matching.engine.services.validators.input.CashInOutOperationInputValidator
import com.swisschain.matching.engine.utils.NumberUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class CashInOutOperationInputValidatorImpl constructor(private val applicationSettingsHolder: ApplicationSettingsHolder) : CashInOutOperationInputValidator {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(CashInOutOperationInputValidatorImpl::class.java.name)
    }

    override fun performValidation(cashInOutParsedData: CashInOutParsedData) {
        val cashInOutContext = cashInOutParsedData.messageWrapper.context as CashInOutContext
        isAssetExist(cashInOutContext, cashInOutParsedData.assetId)
        isFeeValid(cashInOutContext.cashInOutOperation.feeInstructions)
        isAssetEnabled(cashInOutContext)
        isVolumeAccuracyValid(cashInOutContext)
    }

    private fun isAssetExist(cashInOutContext: CashInOutContext, inputAssetId: String) {
        if (cashInOutContext.cashInOutOperation.asset == null) {
            LOGGER.info("Asset with id: $inputAssetId does not exist, cash in/out operation; ${cashInOutContext.cashInOutOperation.externalId}), " +
                    "for client ${cashInOutContext.cashInOutOperation.walletId}")
            throw ValidationException(ValidationException.Validation.UNKNOWN_ASSET)
        }
    }

    private fun isVolumeAccuracyValid(cashInOutContext: CashInOutContext) {
        val amount = cashInOutContext.cashInOutOperation.amount
        val asset = cashInOutContext.cashInOutOperation.asset
        val volumeValid = NumberUtils.isScaleSmallerOrEqual(amount,
                asset!!.accuracy)

        if (!volumeValid) {
            LOGGER.info("Volume accuracy is invalid client: ${cashInOutContext.cashInOutOperation.walletId}, " +
                    "asset: ${asset.symbol}, volume: $amount")
            throw ValidationException(ValidationException.Validation.INVALID_VOLUME_ACCURACY)
        }
    }

    private fun isAssetEnabled(cashInOutContext: CashInOutContext) {
        val amount = cashInOutContext.cashInOutOperation.amount
        val asset = cashInOutContext.cashInOutOperation.asset
        if (amount < BigDecimal.ZERO && applicationSettingsHolder.isAssetDisabled(asset!!.symbol)) {
            LOGGER.info("Cash out operation (${cashInOutContext.cashInOutOperation.externalId}) for client ${cashInOutContext.cashInOutOperation.walletId} " +
                    "asset ${asset.symbol}, " +
                    "volume: ${NumberUtils.roundForPrint(amount)}: disabled asset")
            throw ValidationException(ValidationException.Validation.DISABLED_ASSET)
        }
    }

    private fun isFeeValid(feeInstructions: List<NewFeeInstruction>?) {
        if (!checkFee(feeInstructions)) {
            throw ValidationException(ValidationException.Validation.INVALID_FEE, "invalid fee for client")
        }
    }
}