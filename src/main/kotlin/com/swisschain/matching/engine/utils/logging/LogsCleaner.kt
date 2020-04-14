package com.swisschain.matching.engine.utils.logging

import com.swisschain.matching.engine.utils.config.Config
import com.swisschain.utils.files.clean.LogFilesCleaner
import com.swisschain.utils.files.clean.config.LogFilesCleanerConfig
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
open class LogsCleaner {

    @Autowired
    private lateinit var config: Config

    @Autowired
    @Qualifier("appStarterLogger")
    private lateinit var LOGGER: Logger

    @PostConstruct
    fun startLogsCleaner() {
        try {
            val logFilesCleanerConfig = config.me.logFilesCleaner
            val logFilesCleanerConfigWithDefaults = LogFilesCleanerConfig(logFilesCleanerConfig.enabled,
                    logFilesCleanerConfig.directory,
                    logFilesCleanerConfig.period,
                    logFilesCleanerConfig.uploadDaysThreshold,
                    logFilesCleanerConfig.archiveDaysThreshold)

            LogFilesCleaner.start(logFilesCleanerConfigWithDefaults)
        } catch (e: Exception) {
            LOGGER.error("Unable to start log files cleaner: ${e.message}", e)
        }
    }
}
