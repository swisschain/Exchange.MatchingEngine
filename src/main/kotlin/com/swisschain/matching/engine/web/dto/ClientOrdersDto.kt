package com.swisschain.matching.engine.web.dto

import com.swisschain.matching.engine.daos.LimitOrder

data class ClientOrdersDto(val limitOrders: List<LimitOrder>, val stopOrders: List<LimitOrder>)