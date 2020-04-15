package com.swisschain.matching.engine.daos

class Asset(
        val brokerId: String,
        val symbol: String,
        val name: String,
        val accuracy: Int
){
    override fun toString(): String {
        return "Asset(" +
                "brokerId='$brokerId', " +
                "symbol='$symbol', " +
                "name='$name', " +
                "accuracy=$accuracy)"
    }
}