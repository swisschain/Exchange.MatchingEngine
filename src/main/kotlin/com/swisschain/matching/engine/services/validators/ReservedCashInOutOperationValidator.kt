package com.swisschain.matching.engine.services.validators

import com.swisschain.matching.engine.messages.incoming.IncomingMessages

interface ReservedCashInOutOperationValidator {
    fun performValidation(message: IncomingMessages.ReservedCashInOutOperation)
}