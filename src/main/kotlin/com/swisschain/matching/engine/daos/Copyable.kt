package com.swisschain.matching.engine.daos

interface Copyable {
    fun copy(): Copyable
    fun applyToOrigin(origin: Copyable)
}