package com.swisschain.matching.engine.messages

enum class MessageStatus(val type: Int) {
    OK(2),
    LOW_BALANCE(401),
    DISABLED_ASSET(403),
    UNKNOWN_ASSET(410),
    DUPLICATE(430),
    BAD_REQUEST(400),
    RUNTIME(500),
    MESSAGE_PROCESSING_DISABLED(1),

    // order status
    REPLACED(421),
    NOT_FOUND_PREVIOUS(422),
    RESERVED_VOLUME_HIGHER_THAN_BALANCE(414),
    LIMIT_ORDER_NOT_FOUND(415),
    BALANCE_LOWER_THAN_RESERVED(416),
    LEAD_TO_NEGATIVE_SPREAD(417),
    TOO_SMALL_VOLUME(418),
    INVALID_FEE(419),
    INVALID_PRICE(420),
    NO_LIQUIDITY(411),
    NOT_ENOUGH_FUNDS(412),
    INVALID_VOLUME_ACCURACY(431),
    INVALID_PRICE_ACCURACY(432),
    INVALID_VOLUME(434),
    TOO_HIGH_PRICE_DEVIATION(435),
    INVALID_ORDER_VALUE(436),
    NEGATIVE_OVERDRAFT_LIMIT(433)
}