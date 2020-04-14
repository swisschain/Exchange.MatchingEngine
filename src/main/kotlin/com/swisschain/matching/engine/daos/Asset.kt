package com.swisschain.matching.engine.daos

class Asset(
        val brokerId: String,
        val assetId: String,
        val name: String,
        val accuracy: Int
){
    override fun toString(): String {
        return "Asset(" +
                "brokerId='$brokerId', " +
                "assetId='$assetId', " +
                "name='$name', " +
                "accuracy=$accuracy)"
    }
}