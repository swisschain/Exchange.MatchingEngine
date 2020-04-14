package com.swisschain.matching.engine.daos

data class DisabledFunctionalityRule(
        val brokerId: String,
        val assetId: String?,
        val assetPairId: String?,
        val operationType: OperationType?) {
    fun isEmpty(): Boolean {
        return assetId == null
                && assetPairId == null
                && operationType == null
    }
}