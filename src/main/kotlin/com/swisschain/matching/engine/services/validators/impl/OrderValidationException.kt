package com.swisschain.matching.engine.services.validators.impl

import com.swisschain.matching.engine.exception.MatchingEngineException
import com.swisschain.matching.engine.order.OrderStatus

class OrderValidationException(val orderStatus: OrderStatus, message: String = "") : MatchingEngineException(message)