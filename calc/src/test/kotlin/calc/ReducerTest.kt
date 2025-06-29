package com.moshy.drugcalc.calc.calc

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReducerTest {

    @Test
    fun reducer() {
        val testVals = listOf(
            listOf(1.0, 2.0, 3.0, 4.0, 5.0).toOffsetList(0),
            listOf(6.0, 7.0, 8.0, 9.0, 10.0, 11.0).toOffsetList(5),
            listOf(12.0, 13.0, 14.0, 15.0).toOffsetList(2)
        )
        val expect = listOf(1.0, 2.0, 15.0, 17.0, 19.0, 21.0, 7.0, 8.0, 9.0, 10.0, 11.0)

        assertIterableEquals(expect, reducer(testVals))
    }

}