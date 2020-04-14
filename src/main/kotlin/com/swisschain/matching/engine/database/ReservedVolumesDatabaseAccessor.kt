package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.balance.ReservedVolumeCorrection

interface ReservedVolumesDatabaseAccessor {
    fun addCorrectionsInfo(corrections: List<ReservedVolumeCorrection>)
}