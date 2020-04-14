package com.swisschain.matching.engine.holders

import com.swisschain.matching.engine.database.OrderBookDatabaseAccessor

class OrdersDatabaseAccessorsHolder(val primaryAccessor: OrderBookDatabaseAccessor,
                                    val secondaryAccessor: OrderBookDatabaseAccessor?)