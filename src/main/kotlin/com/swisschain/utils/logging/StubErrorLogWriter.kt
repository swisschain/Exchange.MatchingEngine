package com.swisschain.utils.logging

class StubErrorLogWriter() {
    companion object {
        private val LOGGER = ThrottlingLogger.getLogger(StubErrorLogWriter::class.java.name)
    }

    internal fun log(error: Error) {
        LOGGER.error(error.toString())
    }
}