package com.moshy.drugcalc.calc.datacontroller

import com.moshy.containers.assertIsSortedSet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class PaginationTest {
    @Test
    fun paginateList() {
        val data = listOf(1, 3, 5, 7, 9).assertIsSortedSet()
        val p0 = data.paginateList(PaginationSpecifier(0, null, null))
        val p1 = data.paginateList(PaginationSpecifier(3, 1, PaginationSpecifier.Direction.AFTER))
        val p2 = data.paginateList(PaginationSpecifier(3, 4, PaginationSpecifier.Direction.AFTER))
        val p3 = data.paginateList(PaginationSpecifier(3, 7, PaginationSpecifier.Direction.BEFORE))
        val p4 = data.paginateList(PaginationSpecifier(3, 6, PaginationSpecifier.Direction.BEFORE))

        assertEquals(data, p0)
        assertEquals(listOf(3, 5, 7).assertIsSortedSet(), p1)
        assertEquals(listOf(5, 7, 9).assertIsSortedSet(), p2)
        assertEquals(listOf(1, 3, 5).assertIsSortedSet(), p3)
        assertEquals(listOf(1, 3, 5).assertIsSortedSet(), p4)
    }

}