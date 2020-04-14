package com.swisschain.utils.string

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class StringUtilsTest(private val data: Data) {

    companion object {
        data class Data(
                val sourceStr: String,
                val maxPartLength: Int,
                val maxPartCount: Int,
                val expectedParts: List<String>
        )

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Data> {
            return listOf(
                    Data("1", 1, 1, listOf("1")),
                    Data("12", 1, 1, listOf("12")),
                    Data("123456", 1, 6, listOf("1", "2", "3", "4", "5", "6")),
                    Data("123456", 1, 7, listOf("1", "2", "3", "4", "5", "6")),
                    Data("123456", 1, 5, listOf("1", "2", "3", "4", "56")),
                    Data("123456", 2, 6, listOf("12", "34", "56")),
                    Data("1234567", 2, 3, listOf("12", "34", "567")),
                    Data("1234567", 2, 4, listOf("12", "34", "56", "7")),
                    Data("1234567", 2, 2, listOf("12", "34567")),
                    Data("123456", 4, 6, listOf("1234", "56")),
                    Data("123456", 4, 1, listOf("123456")),
                    Data("123456", 6, 1, listOf("123456")),
                    Data("123456", 8, 1, listOf("123456")),
                    Data("123456", 8, 2, listOf("123456")),
                    Data("123456", 1, 0, listOf())
            )
        }
    }

    @Test
    fun testParts() {
        with(data) {
            assertEquals(expectedParts, sourceStr.parts(maxPartLength, maxPartCount).toList())
        }
    }
}