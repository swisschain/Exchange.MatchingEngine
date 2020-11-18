package com.swisschain.matching.engine.database.stub

import com.swisschain.utils.alivestatus.database.AliveStatusDatabaseAccessor

class StubAliveStatusDatabaseAccessor(): AliveStatusDatabaseAccessor {
    override fun checkAndLock(): Boolean {
        return true
    }

    override fun keepAlive() {
        //do nothing
    }

    override fun unlock() {
        //do nothing
    }
}