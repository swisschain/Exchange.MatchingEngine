package com.swisschain.matching.engine.incoming.grpc

import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.incoming.BalancesMessages
import com.swisschain.matching.engine.incoming.BalancesServiceGrpc
import com.swisschain.matching.engine.outgoing.messages.v2.createProtobufTimestampBuilder
import io.grpc.stub.StreamObserver
import java.util.Date

class BalancesService(private val balancesHolder: BalancesHolder): BalancesServiceGrpc.BalancesServiceImplBase() {

    override fun getAll(request: BalancesMessages.BalancesGetAllRequest,
                        responseObserver: StreamObserver<BalancesMessages.BalancesGetAllResponse>) {
        val now = Date()
        val balances = balancesHolder.getBalances(request.brokerId, request.walletId)
        responseObserver.onNext(buildBalanceAllResponse(now, request.walletId, balances.values))
        responseObserver.onCompleted()
    }

    override fun getByAssetId(request: BalancesMessages.BalancesGetByAssetIdRequest, responseObserver: StreamObserver<BalancesMessages.BalancesGetByAssetIdResponse>) {
        val now = Date()
        val balances = balancesHolder.getBalances(request.brokerId, request.walletId)
        responseObserver.onNext(buildBalanceByIdResponse(now, request.walletId, balances[request.assetId]))
        responseObserver.onCompleted()
    }

    private fun buildBalanceAllResponse(now: Date, walletId: Long, filteredBalances: Collection<AssetBalance>): BalancesMessages.BalancesGetAllResponse {
        val builder = BalancesMessages.BalancesGetAllResponse.newBuilder()
        builder.walletId = walletId
        builder.timestamp = now.createProtobufTimestampBuilder().build()
        filteredBalances.forEach { builder.addBalances(BalancesMessages.Balance.newBuilder().setAssetId(it.asset)
                .setAmount(it.balance.toPlainString()).setReserved(it.reserved.toPlainString()))}
        return builder.build()
    }

    private fun buildBalanceByIdResponse(now: Date, walletId: Long, balance: AssetBalance?): BalancesMessages.BalancesGetByAssetIdResponse {
        val builder = BalancesMessages.BalancesGetByAssetIdResponse.newBuilder()
        builder.walletId = walletId
        builder.timestamp = now.createProtobufTimestampBuilder().build()
        if (balance != null) {
            builder.balance = BalancesMessages.Balance.newBuilder().setAssetId(balance.asset)
                    .setAmount(balance.balance.toPlainString()).setReserved(balance.reserved.toPlainString()).build()
        }
        return builder.build()
    }
}