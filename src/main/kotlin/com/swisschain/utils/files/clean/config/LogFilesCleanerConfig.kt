package com.swisschain.utils.files.clean.config

data class LogFilesCleanerConfig(val enabled: Boolean,
                                 val directory: String?,
                                 val period: Long?,
                                 val uploadDaysThreshold: Int?,
                                 val archiveDaysThreshold: Int?)