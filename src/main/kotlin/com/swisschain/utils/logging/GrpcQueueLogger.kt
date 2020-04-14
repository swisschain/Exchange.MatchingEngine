package com.swisschain.utils.logging

import java.util.concurrent.BlockingQueue

internal class GrpcQueueLogger(grpcConnectionString: String, private val queue: BlockingQueue<Error>) : Thread() {

    private val grpcErrorLogWriter = GrpcErrorLogWriter(grpcConnectionString)

    override fun run() {
        while (true) {
            grpcErrorLogWriter.log(queue.take())
        }
    }
}