package com.swisschain.utils.files.clean.db

import java.io.File

internal class StubLogFilesDatabaseAccessor: LogFilesDatabaseAccessor {
    override fun upload(file: File) {
        //TODO implement old logs upload to grpc
    }
}