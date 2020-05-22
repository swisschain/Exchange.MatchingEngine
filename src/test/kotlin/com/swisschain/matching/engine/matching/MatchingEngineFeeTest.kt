package com.swisschain.matching.engine.matching

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.config.TestApplicationContext
import com.swisschain.matching.engine.daos.FeeType
import com.swisschain.matching.engine.daos.WalletOperation
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildFeeInstructions
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrderFeeInstructions
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildMarketOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import java.math.BigDecimal

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MatchingEngineFeeTest : MatchingEngineTest() {

    @Test
    fun testSellLimitOrderFee() {
        testBalanceHolderWrapper.updateBalance(2, "USD", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(2, "USD", 121.12)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, price = 1.21111, volume = 100.0,
                fees = buildLimitOrderFeeInstructions(
                        type = FeeType.CLIENT_FEE,
                        makerSize = 0.0211111,
                        targetWalletId = 4
                )
        ))

        val limitOrder = buildLimitOrder(walletId = 2, price = 1.2, volume = -200.0,
                fees = buildLimitOrderFeeInstructions(
                        type = FeeType.CLIENT_FEE,
                        takerSize = 0.01,
                        targetWalletId = 3
                )
        )

        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER, null, 2, "EUR", BigDecimal.valueOf(-100.0), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null, 2, "USD", BigDecimal.valueOf(119.89), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null, 3, "USD", BigDecimal.valueOf(1.22), BigDecimal.ZERO)
                ),
                matchingResult.ownCashMovements
        )

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER, null,  1, "EUR", BigDecimal.valueOf(97.8888), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  1, "USD", BigDecimal.valueOf(-121.11), BigDecimal.valueOf(-121.11)),
                        WalletOperation(DEFAULT_BROKER, null,  4, "EUR", BigDecimal.valueOf(2.1112), BigDecimal.ZERO)
                ),
                matchingResult.oppositeCashMovements
        )
    }

    @Test
    fun testBuyLimitOrderFee() {
        testBalanceHolderWrapper.updateBalance(2, "EUR", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(2, "EUR", 100.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0,
                fees = buildLimitOrderFeeInstructions(
                        type = FeeType.CLIENT_FEE,
                        makerSize = 0.02,
                        targetWalletId = 4
                )
        ))

        val limitOrder = buildLimitOrder(walletId = 1, price = 1.2, volume = 200.0,
                fees = buildLimitOrderFeeInstructions(
                        type = FeeType.CLIENT_FEE,
                        takerSize = 0.01,
                        targetWalletId = 3
                )
        )

        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER, null,  1, "EUR", BigDecimal.valueOf(99.0), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  1, "USD", BigDecimal.valueOf(-120.0), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  3, "EUR", BigDecimal.valueOf(1.0), BigDecimal.ZERO)
                ),
                matchingResult.ownCashMovements
        )

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER, null,  2, "EUR", BigDecimal.valueOf(-100.0), BigDecimal.valueOf(-100.0)),
                        WalletOperation(DEFAULT_BROKER, null,  2, "USD", BigDecimal.valueOf(117.6), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  4, "USD", BigDecimal.valueOf(2.4), BigDecimal.ZERO)
                ),
                matchingResult.oppositeCashMovements
        )
    }

    @Test
    fun testSellMarketOrderFee() {
        testBalanceHolderWrapper.updateBalance(1, "USD", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(1, "USD", 120.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 1, price = 1.2, volume = 100.0,
                fees = buildLimitOrderFeeInstructions(
                        type = FeeType.CLIENT_FEE,
                        makerSize = 0.02,
                        targetWalletId = 4
                )
        ))

        val limitOrder = buildMarketOrder(walletId = 2, volume = -100.0,
                fees = buildFeeInstructions(
                        type = FeeType.CLIENT_FEE,
                        size = 0.01,
                        targetWalletId = 3
                )
        )

        val matchingResult = match(limitOrder, getOrderBook("EURUSD", true))

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER, null,  2, "EUR", BigDecimal.valueOf(-100.0), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  2, "USD", BigDecimal.valueOf(118.8), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  3, "USD", BigDecimal.valueOf(1.2), BigDecimal.ZERO)
                ),
                matchingResult.ownCashMovements
        )

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER, null,  1, "EUR", BigDecimal.valueOf(98.0), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  1, "USD", BigDecimal.valueOf(-120.0), BigDecimal.valueOf(-120.0)),
                        WalletOperation(DEFAULT_BROKER, null,  4, "EUR", BigDecimal.valueOf(2.0), BigDecimal.ZERO)
                ),
                matchingResult.oppositeCashMovements
        )
    }

    @Test
    fun testBuyMarketOrderFee() {
        testBalanceHolderWrapper.updateBalance(2, "EUR", 1000.0)
        testBalanceHolderWrapper.updateReservedBalance(2, "EUR", 100.0)
        testOrderBookWrapper.addLimitOrder(buildLimitOrder(walletId = 2, price = 1.2, volume = -100.0,
                fees = buildLimitOrderFeeInstructions(
                        type = FeeType.CLIENT_FEE,
                        makerSize = 0.02,
                        targetWalletId = 4
                )
        ))

        val limitOrder = buildMarketOrder(walletId = 1, volume = 100.0,
                fees = buildFeeInstructions(
                        type = FeeType.CLIENT_FEE,
                        size = 0.01,
                        targetWalletId = 3
                )
        )

        val matchingResult = match(limitOrder, getOrderBook("EURUSD", false))

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER, null,  1, "EUR", BigDecimal.valueOf(99.0), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  1, "USD", BigDecimal.valueOf(-120.0), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  3, "EUR", BigDecimal.valueOf(1.0), BigDecimal.ZERO)
                ),
                matchingResult.ownCashMovements
        )

        assertCashMovementsEquals(
                listOf(
                        WalletOperation(DEFAULT_BROKER, null,  2, "EUR", BigDecimal.valueOf(-100.0), BigDecimal.valueOf(-100.0)),
                        WalletOperation(DEFAULT_BROKER, null,  2, "USD", BigDecimal.valueOf(117.6), BigDecimal.ZERO),
                        WalletOperation(DEFAULT_BROKER, null,  4, "USD", BigDecimal.valueOf(2.4), BigDecimal.ZERO)
                ),
                matchingResult.oppositeCashMovements
        )
    }

}