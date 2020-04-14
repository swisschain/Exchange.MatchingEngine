package com.swisschain.utils.files.clean.db

import java.io.File

internal interface LogFilesDatabaseAccessor {
    fun upload(file: File)
}