package com.swisschain.utils.logging

import java.util.concurrent.BlockingQueue

internal class StubQueueLogger(private val queue: BlockingQueue<Error>) : Thread() {

    private val stubErrorLogWriter = StubErrorLogWriter()

    override fun run() {
        while (true) {
            stubErrorLogWriter.log(queue.take())
        }
    }
}