package com.swisschain.matching.engine.order.transaction

import com.swisschain.matching.engine.AbstractTest.Companion.DEFAULT_BROKER
import com.swisschain.matching.engine.order.OrderStatus
import com.swisschain.matching.engine.services.AssetOrderBook
import com.swisschain.matching.engine.services.GenericLimitOrderService
import com.swisschain.matching.engine.utils.MessageBuilder.Companion.buildLimitOrder
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.Date
import kotlin.test.assertEquals

class CurrentTransactionOrderBooksHolderTest {

    private lateinit var currentTransactionOrderBooksHolder: CurrentTransactionOrderBooksHolder

    @Before
    fun setUp() {
        val genericLimitOrderService = Mockito.mock(GenericLimitOrderService::class.java)

        Mockito.`when`(genericLimitOrderService.getOrderBook(DEFAULT_BROKER,"EURUSD"))
                .thenReturn(AssetOrderBook(DEFAULT_BROKER,"EURUSD"))

        currentTransactionOrderBooksHolder = CurrentTransactionOrderBooksHolder(genericLimitOrderService)
    }

    @Test
    fun testGetPersistenceDataAfterCreatingAndChangingCopyOfNewOrder() {
        val order = buildLimitOrder(assetId = "EURUSD", status = "Status1", uid = "NewOrderToChange")
        currentTransactionOrderBooksHolder.addOrder(order)
        currentTransactionOrderBooksHolder.addOrder(buildLimitOrder(assetId = "EURUSD", uid = "OtherNewOrder"))
        currentTransactionOrderBooksHolder.getOrPutOrderCopyWrapper(order)
                .copy
                .updateStatus(OrderStatus.Processing, Date())

        val persistenceData = currentTransactionOrderBooksHolder.getPersistenceData()

        assertEquals(2, persistenceData.ordersToSave.size)
        assertEquals(OrderStatus.Processing.name, persistenceData.ordersToSave.single { it.externalId == "NewOrderToChange" }.status)
    }
}