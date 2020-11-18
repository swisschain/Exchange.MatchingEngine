package com.swisschain.matching.engine.database.stub

import com.swisschain.matching.engine.daos.balance.ReservedVolumeCorrection
import com.swisschain.matching.engine.database.ReservedVolumesDatabaseAccessor
import com.swisschain.utils.logging.ThrottlingLogger

class StubReservedVolumeCorrectionDatabaseAccessor(): ReservedVolumesDatabaseAccessor {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(StubReservedVolumeCorrectionDatabaseAccessor::class.java.name)
    }

    override fun addCorrectionsInfo(corrections: List<ReservedVolumeCorrection>) {
        corrections.forEach { LOGGER.debug(it.toString()) }
    }
}