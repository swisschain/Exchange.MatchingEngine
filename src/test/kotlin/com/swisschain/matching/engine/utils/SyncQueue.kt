package com.swisschain.matching.engine.utils

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class SyncQueue<T> : BlockingQueue<T> {

    private val queue = LinkedBlockingQueue<T>()

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private var prevEvent: T? = null
    private var curEvent: T? = null

    override fun containsAll(elements: Collection<T>): Boolean {
        throw Exception()
    }

    override fun contains(element: T): Boolean {
        throw Exception()
    }

    override fun addAll(elements: Collection<T>): Boolean {
        throw Exception()
    }

    override fun clear() {
        throw Exception()
    }

    override fun element(): T {
        throw Exception()
    }

    override fun take(): T {
        lock.lock()
        try {
            prevEvent = curEvent
            condition.signal()
        } finally {
            lock.unlock()
        }
        curEvent = queue.take()
        return curEvent!!
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        throw Exception()
    }

    override fun add(element: T): Boolean {
        throw Exception()
    }

    override fun offer(e: T): Boolean {
        throw Exception()
    }

    override fun offer(e: T, timeout: Long, unit: TimeUnit?): Boolean {
        throw Exception()
    }

    override fun iterator(): MutableIterator<T> {
        throw Exception()
    }

    override fun peek(): T {
        throw Exception()
    }

    override fun put(e: T) {
        lock.lock()
        try {
            val event = e
            queue.put(event)
            while (prevEvent != event) {
                condition.await()
            }
        } finally {
            lock.unlock()
        }
    }

    override fun isEmpty(): Boolean {
        throw Exception()
    }

    override fun remove(element: T): Boolean {
        throw Exception()
    }

    override fun remove(): T {
        throw Exception()
    }

    override fun drainTo(c: MutableCollection<in T>?): Int {
        throw Exception()
    }

    override fun drainTo(c: MutableCollection<in T>?, maxElements: Int): Int {
        throw Exception()
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        throw Exception()
    }

    override fun remainingCapacity(): Int {
        throw Exception()
    }

    override fun poll(timeout: Long, unit: TimeUnit?): T {
        throw Exception()
    }

    override fun poll(): T {
        throw Exception()
    }

    override val size: Int
        get() = throw Exception()
}