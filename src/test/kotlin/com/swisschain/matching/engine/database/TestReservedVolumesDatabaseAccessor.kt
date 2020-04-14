package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.daos.balance.ReservedVolumeCorrection

class TestReservedVolumesDatabaseAccessor : ReservedVolumesDatabaseAccessor {
    val corrections = ArrayList<ReservedVolumeCorrection>()

    override fun addCorrectionsInfo(corrections: List<ReservedVolumeCorrection>) {
        this.corrections.addAll(corrections)
    }
}