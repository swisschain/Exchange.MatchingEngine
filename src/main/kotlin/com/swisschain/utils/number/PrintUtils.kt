package com.swisschain.utils.number

import com.swisschain.matching.engine.utils.RoundingUtils

class PrintUtils {
    companion object {
        fun convertToString(value: Double): String {
            return if ((value / 100000).toInt() == 0) {
                //microseconds
                "${RoundingUtils.roundForPrint(value / 1000)} micros"
            } else {
                //milliseconds
                "${RoundingUtils.roundForPrint(value / 1000000)} millis"
            }
        }
    }
}