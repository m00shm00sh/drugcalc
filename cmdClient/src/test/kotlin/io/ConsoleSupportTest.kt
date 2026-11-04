package com.moshy.drugcalc.cmdclient.io

import com.moshy.drugcalc.types.dataentry.BlendName
import com.moshy.drugcalc.types.dataentry.CompoundBase
import com.moshy.drugcalc.types.dataentry.FrequencyName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class ConsoleSupportTest {
    @Test
    fun `quote leaves unspaced strings alone`() {
        assertEquals("abc", quote("abc"))
    }

    @Test
    fun `quote wraps strings containing spaces`() {
        assertEquals("\"a b\"", quote("a b"))
    }

    @Test
    fun `quote escapes embedded quotes`() {
        assertEquals("\"a \\\"b\\\" c\"", quote("a \"b\" c"))
    }

    @Test
    fun `quote overloads unwrap value classes`() {
        assertEquals("\"a b\"", quote(CompoundBase("a b")))
        assertEquals("\"a b\"", quote(BlendName("a b")))
        assertEquals("\"a b\"", quote(FrequencyName("a b")))
    }
}
