package com.swisschain.matching.engine.holders

import com.swisschain.matching.engine.database.StopOrderBookDatabaseAccessor

class StopOrdersDatabaseAccessorsHolder(val primaryAccessor: StopOrderBookDatabaseAccessor,
                                        val secondaryAccessor: StopOrderBookDatabaseAccessor?)