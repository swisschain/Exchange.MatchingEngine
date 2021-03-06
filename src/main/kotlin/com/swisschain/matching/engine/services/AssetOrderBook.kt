package com.swisschain.matching.engine.services

import com.swisschain.matching.engine.daos.LimitOrder
import com.swisschain.matching.engine.services.utils.AbstractAssetOrderBook
import java.math.BigDecimal
import java.util.Arrays
import java.util.Comparator
import java.util.concurrent.PriorityBlockingQueue

open class AssetOrderBook(brokerId: String, assetId: String) : AbstractAssetOrderBook(brokerId, assetId) {
    companion object {
        private val SELL_COMPARATOR = Comparator<LimitOrder> { o1, o2 ->
            var result = o1.price.compareTo(o2.price)
            if (result == 0) {
                result = o1.createdAt.compareTo(o2.createdAt)
            }

            result
        }

        private val BUY_COMPARATOR = Comparator<LimitOrder> { o1, o2 ->
            var result = o2.price.compareTo(o1.price)
            if (result == 0) {
                result = o1.createdAt.compareTo(o2.createdAt)
            }

            result
        }

        fun sort(isBuySide: Boolean, orders: Array<LimitOrder>): Array<LimitOrder> {
            Arrays.sort(orders, if (isBuySide) BUY_COMPARATOR else SELL_COMPARATOR)
            return orders
        }
    }


    private var askOrderBook = PriorityBlockingQueue<LimitOrder>(50, SELL_COMPARATOR)
    private var bidOrderBook = PriorityBlockingQueue<LimitOrder>(50, BUY_COMPARATOR)

    override fun getOrderBook(isBuySide: Boolean) = if (isBuySide) bidOrderBook else askOrderBook

    fun setOrderBook(isBuySide: Boolean, queue: PriorityBlockingQueue<LimitOrder>) = if (isBuySide) bidOrderBook = queue else askOrderBook = queue

    override fun addOrder(order: LimitOrder) = getOrderBook(order.isBuySide()).add(order)

    override fun removeOrder(order: LimitOrder) = getOrderBook(order.isBuySide()).remove(order)

    fun getAskPrice() = askOrderBook.peek()?.price ?: BigDecimal.ZERO
    fun getBidPrice() = bidOrderBook.peek()?.price ?: BigDecimal.ZERO

    fun leadToNegativeSpread(order: LimitOrder): Boolean {
        val book = getOrderBook(!order.isBuySide())
        if (book.isEmpty()) {
            return false
        }

        val bestPrice = book.peek().price
        if (order.isBuySide()) {
            return order.price >= bestPrice
        } else {
            return order.price <= bestPrice
        }
    }

    override fun copy(): AssetOrderBook {
        val book = AssetOrderBook(brokerId, assetPairId)

        askOrderBook.forEach {
            book.askOrderBook.put(it)
        }

        bidOrderBook.forEach {
            book.bidOrderBook.put(it)
        }

        return book
    }
}