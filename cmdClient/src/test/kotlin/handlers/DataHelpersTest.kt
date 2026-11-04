package com.moshy.drugcalc.cmdclient.handlers

import com.moshy.drugcalc.types.dataentry.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.days

internal class DataHelpersTest {
    @Test
    fun `dataApiBase distinguishes whole-data and named sections`() {
        assertEquals("/api/data/", dataApiBase(null))
        assertEquals("/api/data/compounds", dataApiBase("compounds"))
    }

    @Test
    fun `apiLink builds compound links`() {
        assertEquals("/api/data/compounds", apiLink(null as CompoundName?))
        assertEquals(
            "/api/data/compounds/foo+bar",
            apiLink(CompoundName.selectAllVariants(CompoundBase("foo bar")))
        )
        assertEquals(
            "/api/data/compounds/foo+bar/-",
            apiLink(CompoundName(CompoundBase("foo bar")))
        )
        assertEquals(
            "/api/data/compounds/foo+bar/v+1",
            apiLink(CompoundName(CompoundBase("foo bar"), "v 1"))
        )
    }

    @Test
    fun `apiLink builds blend and frequency links`() {
        assertEquals("/api/data/blends", apiLink(null as BlendName?))
        assertEquals("/api/data/blends/mix+one", apiLink(BlendName("mix one")))

        assertEquals("/api/data/frequencies", apiLink(null as FrequencyName?))
        assertEquals("/api/data/frequencies/f+1", apiLink(FrequencyName("f 1")))
    }

    @Test
    fun `reshape names groups variants under their base`() {
        val names = setOf(
            CompoundName("foo"),
            CompoundName(CompoundBase("foo"), "v1"),
            CompoundName("bar"),
        )

        assertEquals(
            mapOf(
                CompoundBase("foo") to setOf("", "v1"),
                CompoundBase("bar") to setOf(""),
            ),
            names.reshapeByBaseAndVariant()
        )
    }

    @Test
    fun `reshape values groups values under their base`() {
        val values = mapOf(
            CompoundName("foo") to 1,
            CompoundName(CompoundBase("foo"), "v1") to 2,
            CompoundName("bar") to 3,
        )

        assertEquals(
            mapOf(
                CompoundBase("foo") to mapOf("" to 1, "v1" to 2),
                CompoundBase("bar") to mapOf("" to 3),
            ),
            values.reshapeByBaseAndVariant()
        )
    }

    @Test
    fun `reshape rejects select-all-variants names`() {
        val names = setOf(CompoundName.selectAllVariants("foo"))

        assertThrows<IllegalArgumentException> {
            names.reshapeByBaseAndVariant()
        }
    }

    @Test
    fun `appendData renders compounds blends and frequencies`() {
        val data = Data(
            compounds = mapOf(
                CompoundName(CompoundBase("foo"), "v1") to CompoundInfo(1.days),
                CompoundName(CompoundBase("bar"), "bz") to CompoundInfo(2.days),
            ),
            blends = mapOf(
                BlendName("mix") to BlendValue(
                    mapOf(
                        CompoundName("foo") to 1.0,
                        CompoundName("bar") to 2.0,
                    )
                )
            ),
            frequencies = mapOf(
                FrequencyName("f1") to FrequencyValue(listOf(1.days))
            )
        )
        val lines = mutableListOf<String>()

        lines.appendData(data)

        assertEquals(
            listOf(
                "compounds",
                "\tfoo: v1",
                "\tbar: bz",
                "blends",
                "\tmix",
                "frequencies",
                "\tf1",
            ),
            lines
        )
    }

    @Test
    fun `appendDataSummary only includes non-empty sections`() {
        val data = Data(
            compounds = mapOf(
                CompoundName("foo") to CompoundInfo(1.days),
                CompoundName("bar") to CompoundInfo(2.days),
            ),
            blends = mapOf(
                BlendName("mix") to BlendValue(
                    mapOf(
                        CompoundName("foo") to 1.0,
                        CompoundName("bar") to 2.0,
                    )
                )
            ),
            frequencies = mapOf(
                FrequencyName("f1") to FrequencyValue(listOf(1.days))
            )
        )
        val lines = mutableListOf<String>()

        lines.appendDataSummary(data, indent = 1)

        assertEquals(
            listOf("\tcompound entries: 2", "\tblends: 1", "\tfrequencies: 1"),
            lines
        )

        val empty = mutableListOf<String>()
        empty.appendDataSummary(Data())
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `toCompoundUpdater parses canonical properties`() {
        val info = mapOf(
            "halfLife" to "1.5d",
            "pctActive" to "0.5",
            "note" to "n",
        ).toCompoundUpdater().createObject()

        assertEquals(CompoundInfo(1.5.days, 0.5, "n"), info)
    }

    @Test
    fun `toCompoundUpdater accepts a partial update`() {
        val info = mapOf("halfLife" to "1d").toCompoundUpdater().createObject()

        assertEquals(CompoundInfo(1.days), info)
    }

    @Test
    fun `toCompoundUpdater rejects an invalid duration`() {
        assertThrows<IllegalArgumentException> {
            mapOf("halfLife" to "not-a-duration").toCompoundUpdater()
        }
    }

    @Test
    fun `checkImport merges when there is no conflict`() {
        val result = checkImport(
            mapOf("a" to 1),
            "compounds",
            toLoad = mapOf("b" to 2),
            allowOverwrite = false,
        )

        assertEquals(mapOf("a" to 1, "b" to 2), result)
    }

    @Test
    fun `checkImport rejects conflicts when overwrite is disabled`() {
        val ex = assertThrows<IllegalArgumentException> {
            checkImport(
                mapOf("a" to 1),
                "compounds",
                toLoad = mapOf("a" to 2),
                allowOverwrite = false,
            )
        }

        assertTrue(ex.message!!.contains("compounds: conflict(s): [a]"))
    }

    @Test
    fun `checkImport overwrites when allowed`() {
        val result = checkImport(
            mapOf("a" to 1),
            "compounds",
            toLoad = mapOf("a" to 2),
            allowOverwrite = true,
        )

        assertEquals(mapOf("a" to 2), result)
    }

    @Test
    fun `checkImport rejects other conflicts regardless of overwrite`() {
        val ex = assertThrows<IllegalArgumentException> {
            checkImport(
                mapOf("a" to 1),
                "compounds",
                otherConflictKeys = setOf("b"),
                toLoad = mapOf("b" to 2),
                allowOverwrite = true,
            )
        }

        assertTrue(ex.message!!.contains("other conflict(s): [b]"))
    }

    @Test
    fun `mutable checkImport updates the existing map`() {
        val existing: MutableMap<Comparable<*>, Any> = mutableMapOf("a" to 1)

        val result = checkImport(
            existing,
            "compounds",
            toLoad = mapOf<Comparable<*>, Any>("b" to 2),
            allowOverwrite = false,
        )

        assertSame(existing, result)
        assertEquals(mapOf("a" to 1, "b" to 2), existing)
    }
}
