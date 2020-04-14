package com.swisschain.utils.alivestatus.processor

import com.swisschain.utils.AppInitializer
import com.swisschain.utils.alivestatus.config.AliveStatusConfig
import com.swisschain.utils.alivestatus.database.AliveStatusDatabaseAccessor
import com.swisschain.utils.alivestatus.exception.CheckAppInstanceRunningException
import org.slf4j.LoggerFactory
import kotlin.concurrent.fixedRateTimer

class AliveStatusProcessor internal constructor(
        private val dbAccessor: AliveStatusDatabaseAccessor,
        private val config: AliveStatusConfig
) : Runnable {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(AliveStatusProcessor::class.java.name)
    }

    private var isAlive = false

    override fun run() {
        if (!dbAccessor.checkAndLock()) {
            throw CheckAppInstanceRunningException("Another app instance is already running")
        }
        isAlive = true

        fixedRateTimer(name = "AliveStatusUpdater", initialDelay = config.updatePeriod, period = config.updatePeriod) {
            try {
                keepAlive()
            } catch (e: Exception) {
                val errorMessage = "Unable to save a keep alive status: ${e.message}"
                AppInitializer.teeLog(errorMessage)
                LOGGER.error(errorMessage, e)
            }
        }

        Runtime.getRuntime().addShutdownHook(AliveStatusShutdownHook(this))
    }

    @Synchronized
    private fun keepAlive() {
        synchronized(this) {
            if (!isAlive) {
                return
            }
            dbAccessor.keepAlive()
        }
    }

    @Synchronized
    internal fun unlock() {
        synchronized(this) {
            dbAccessor.unlock()
            isAlive = false
        }
    }
}

