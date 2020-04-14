package com.swisschain.matching.engine.database

import com.swisschain.matching.engine.database.common.entity.PersistenceData

interface PersistenceManager {
    fun persist(data: PersistenceData): Boolean
}