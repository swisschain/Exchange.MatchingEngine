package com.swisschain.matching.engine.services.validators.impl

import com.swisschain.matching.engine.order.OrderStatus

class OrderValidationResult(val isValid: Boolean,
                            val isFatalInvalid: Boolean = false,
                            val message: String? = null,
                            val status: OrderStatus? = null)
