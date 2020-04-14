package com.swisschain.utils.logging

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsLoggerTest {

    companion object {
        val LOGGER = LoggerFactory.getLogger(MetricsLoggerTest::class.java)!!
    }

    @Before
    fun setUp() {
        MetricsLogger.ERROR_QUEUE.clear()
    }

    @After
    fun tearDown() {
        MetricsLogger.ERROR_QUEUE.clear()
        MetricsLogger.sentTimestamps.clear()
    }

    @Test
    fun testThrottling() {

        val metricsLogger = MetricsLogger.getLogger()

        MetricsLogger.throttlingLimit = 3000

        val queue = MetricsLogger.ERROR_QUEUE

        metricsLogger.logError("TestErrorMessage")
        metricsLogger.logError("TestErrorMessage")
        metricsLogger.logError("TestErrorMessage")

        assertEquals(1, queue.size)

        Thread.sleep(3100)

        metricsLogger.logError("TestErrorMessage")
        metricsLogger.logError("TestErrorMessage")
        metricsLogger.logError("TestErrorMessage")

        assertEquals(2, queue.size)

        metricsLogger.logError("TestErrorMessage2")
        metricsLogger.logError("TestErrorMessage2")

        assertEquals(3, queue.size)
    }

    @Test
    fun testCleanMessages() {

        val timestamp = Date().time - 60 * 60 * 1000
        MetricsLogger.sentTimestamps.put("ErrorMessage1", timestamp)
        MetricsLogger.sentTimestamps.put("ErrorMessage2", timestamp)
        MetricsLogger.sentTimestamps.put("ErrorMessage3", timestamp)
        MetricsLogger.sentTimestamps.put("ErrorMessage5", Date().time)

        MetricsLogger.clearSentMessageTimestamps(ttlMinutes = 30)

        assertEquals(1, MetricsLogger.sentTimestamps.size)

    }

    @Test
    fun testConcurrentAddAndClean() {

        MetricsLogger.sentTimestamps["1"] = 1
        MetricsLogger.sentTimestamps["2"] = 2
        MetricsLogger.sentTimestamps["3"] = 3
        MetricsLogger.sentTimestamps["4"] = 4
        MetricsLogger.sentTimestamps["5"] = 5

        var thread1Finished = true
        var thread2Finished = true

        val thread1 = Thread({
            try {
                val iterator = MetricsLogger.sentTimestamps.iterator()
                Thread.sleep(10)
                while (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }

            } catch (e: Exception) {
                LOGGER.error("", e)
                thread1Finished = false
            }
        }, "Thread1")


        val thread2 = Thread({
            try {
                for (i in 10L..100L) {
                    MetricsLogger.sentTimestamps[i.toString()] = i
                }
            } catch (e: Exception) {
                LOGGER.error("", e)
                thread2Finished = false
            }
        }, "Thread2")

        thread1.start()
        thread2.start()

        thread1.join()
        thread2.join()

        assertTrue { thread1Finished }
        assertTrue { thread2Finished }

    }
}