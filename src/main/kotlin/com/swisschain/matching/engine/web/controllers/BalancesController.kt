package com.swisschain.matching.engine.web.controllers

import com.swisschain.matching.engine.daos.wallet.AssetBalance
import com.swisschain.matching.engine.holders.BalancesHolder
import com.swisschain.matching.engine.web.dto.BalanceDto
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Api(description = "Read only api, returns balance information for supplied client")
class BalancesController {
    @Autowired
    private lateinit var balancesHolder: BalancesHolder

    @GetMapping("/balances", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ApiOperation("Returns balance information for supplied client and assetId")
    fun getBalances(@RequestParam("brokerId") brokerId: String,
                    @RequestParam("walletId") walletId: String,
                    @RequestParam(name = "assetId", required = false, defaultValue = "") assetId: String): ResponseEntity<*> {

        val balances = balancesHolder.getBalances(brokerId, walletId)

        if (balances.isEmpty()) {
            return ResponseEntity("Requested client has no balances", HttpStatus.NOT_FOUND)
        }

        if (assetId.isNotBlank()) {
            val clientBalance = balances[assetId] ?: return ResponseEntity("No balance found for client, for supplied asset", HttpStatus.NOT_FOUND)
            return ResponseEntity.ok(listOf(toBalanceDto(assetId, clientBalance)))
        }

        return ResponseEntity.ok(balances
                .mapValues { entry -> toBalanceDto(entry.value.asset, entry.value) }
                .values
                .toList())
    }

    private fun toBalanceDto(assetId: String, assetBalance: AssetBalance): BalanceDto? {
        return BalanceDto(assetId, assetBalance.balance, assetBalance.reserved)
    }
}