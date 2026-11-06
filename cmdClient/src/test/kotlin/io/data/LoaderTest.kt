package com.moshy.drugcalc.cmdclient.io.data

import com.moshy.drugcalc.types.dataentry.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.days

internal class LoaderTest {
    @TempDir
    lateinit var tempDir: Path

    private fun write(name: String, content: String): String =
        tempDir.resolve(name).also { Files.writeString(it, content) }.toString()

    private fun writeTrimmed(name: String, content: String): String =
        write(name, content.trimIndent())

    @Test
    fun `loadCompounds parses JSON`() {
        val file = writeTrimmed(
            "compounds.json",
            """
            {
              "foo": {"halfLife": "1d"},
              "bar=baz": {"halfLife": "2d", "pctActive": 0.5, "note": "n"}
            }
            """
        )

        val loaded = loadCompounds(file)
        val barBaz = CompoundName(CompoundBase("bar"), "baz")

        assertEquals(setOf(CompoundName("foo"), barBaz), loaded.keys)
        assertEquals(CompoundInfo(1.days), loaded[CompoundName("foo")])
        assertEquals(CompoundInfo(2.days, 0.5, "n"), loaded[barBaz])
    }

    @Test
    fun `loadCompounds parses CSV and hyphen markers`() {
        val file = writeTrimmed(
            "compounds.csv",
            """
            compound,variant,halfLife,pctActive,note
            foo,,1d,,hello
            bar,baz,2d,0.5,-
            """
        )

        val loaded = loadCompounds(file)

        assertEquals(setOf(CompoundName("foo"), CompoundName(CompoundBase("bar"), "baz")), loaded.keys)
        assertEquals(CompoundInfo(1.days, note = "hello"), loaded[CompoundName("foo")])
        assertEquals(CompoundInfo(2.days, 0.5, note = ""), loaded[CompoundName(CompoundBase("bar"), "baz")])
    }

    @Test
    fun `loadCompoundsUpdater keeps only supplied properties`() {
        val file = writeTrimmed(
            "compounds-update.json",
            """
            {
              "foo": {"halfLife": "1d"},
              "bar": {"pctActive": 0.5}
            }
            """
        )

        val loaded = loadCompoundsUpdater(file)
        val foo = loaded[CompoundName("foo")]!!
        val bar = loaded[CompoundName("bar")]!!

        assertEquals(1.days, foo["halfLife"])
        assertFalse("pctActive" in foo)
        assertFalse("note" in foo)
        assertEquals(0.5, bar["pctActive"])
        assertFalse("halfLife" in bar)
    }

    @Test
    fun `loadBlends parses JSON`() {
        val file = writeTrimmed(
            "blends.json",
            """
            {
              "mix": {
                "components": {"foo": 1.0, "bar=baz": 2.0},
                "note": "bn"
              }
            }
            """
        )

        val loaded = loadBlends(file)
        val expectedComponents = mapOf(
            CompoundName("foo") to 1.0,
            CompoundName(CompoundBase("bar"), "baz") to 2.0
        )

        assertEquals(BlendValue(expectedComponents, "bn"), loaded[BlendName("mix")])
    }

    @Test
    fun `loadBlends parses CSV`() {
        val file = writeTrimmed(
            "blends.csv",
            """
            name,componentCompound,componentVariant,componentDose,note
            mix,foo,fv,1,blend note
            mix,bar,bz,2,
            """
        )

        val loaded = loadBlends(file)
        val expectedComponents = mapOf(
            CompoundName(CompoundBase("foo"), "fv") to 1.0,
            CompoundName(CompoundBase("bar"), "bz") to 2.0
        )

        assertEquals(BlendValue(expectedComponents, "blend note"), loaded[BlendName("mix")])
    }

    @Test
    fun `loadFrequencies parses JSON`() {
        val file = writeTrimmed(
            "frequencies.json",
            """
            {
              "f1": {"values": ["1d", "2d"]},
              "f2": {"values": ["3d"]}
            }
            """
        )

        val loaded = loadFrequencies(file)

        assertEquals(FrequencyValue(listOf(1.days, 2.days)), loaded[FrequencyName("f1")])
        assertEquals(FrequencyValue(listOf(3.days)), loaded[FrequencyName("f2")])
    }

    @Test
    fun `loadFrequencies parses CSV`() {
        val file = writeTrimmed(
            "frequencies.csv",
            """
            name,time
            f1,1d
            f1,2d
            f2,3d
            """
        )

        val loaded = loadFrequencies(file)

        assertEquals(FrequencyValue(listOf(1.days, 2.days)), loaded[FrequencyName("f1")])
        assertEquals(FrequencyValue(listOf(3.days)), loaded[FrequencyName("f2")])
    }

    @Test
    fun `loadData parses XCSV sections`() {
        val file = writeTrimmed(
            "data.xcsv",
            """
            !section compounds
            compound,variant,halfLife,pctActive,note
            foo,,1d,,hello
            !section blends
            name,componentCompound,componentVariant,componentDose,note
            mix,foo,fv,1,blend note
            mix,bar,bz,2,
            !section frequencies
            name,time
            f1,1d
            f1,2d
            """
        )

        val data = loadData(file)

        assertEquals(
            mapOf(CompoundName("foo") to CompoundInfo(1.days, note = "hello")),
            data.compounds
        )
        assertEquals(
            mapOf(
                BlendName("mix") to BlendValue(
                    mapOf(
                        CompoundName(CompoundBase("foo"), "fv") to 1.0,
                        CompoundName(CompoundBase("bar"), "bz") to 2.0
                    ),
                    "blend note"
                )
            ),
            data.blends
        )
        assertEquals(
            mapOf(FrequencyName("f1") to FrequencyValue(listOf(1.days, 2.days))),
            data.frequencies
        )
    }

    @Test
    fun `loadData returns empty maps for missing XCSV sections`() {
        val file = writeTrimmed(
            "compounds-only.xcsv",
            """
            !section compounds
            compound,variant,halfLife,pctActive,note
            foo,,1d,,
            """
        )

        val data = loadData(file)

        assertEquals(mapOf(CompoundName("foo") to CompoundInfo(1.days)), data.compounds)
        assertTrue(data.blends.isEmpty())
        assertTrue(data.frequencies.isEmpty())
    }

    @Test
    fun `loadData rejects plain CSV`() {
        val file = writeTrimmed(
            "plain.csv",
            """
            compound,variant,halfLife,pctActive,note
            foo,,1d,,
            """
        )

        val ex = assertThrows<IllegalArgumentException> { loadData(file) }
        assertEquals("bad input: detected normal CSV", ex.message)
    }

    @Test
    fun `loadData rejects unrecognized input`() {
        val file = write("unknown.bin", "not a data file")

        val ex = assertThrows<IllegalStateException> { loadData(file) }
        assertTrue(ex.message!!.startsWith("couldn't determine file type"))
    }

    @Test
    fun `blend note does not leak to the next blend`() {
        val file = writeTrimmed(
            "stale-note.xcsv",
            """
            !section blends
            name,componentCompound,componentVariant,componentDose,note
            blendA,foo,fa,1,note A
            blendA,bar,ba,2,
            blendB,foo,fb,3,
            blendB,bar,bb,4,
            """
        )

        val data = loadData(file)

        assertEquals("note A", data.blends[BlendName("blendA")]!!.note)
        assertEquals("", data.blends[BlendName("blendB")]!!.note)
    }

    @Test
    fun `blend note uses first non-null value`() {
        val file = writeTrimmed(
            "late-note.xcsv",
            """
            !section blends
            name,componentCompound,componentVariant,componentDose,note
            blendA,foo,fa,1,
            blendA,bar,ba,2,note A
            """
        )

        val data = loadData(file)

        assertEquals("note A", data.blends[BlendName("blendA")]!!.note)
    }

    @Test
    fun `blend rejects conflicting notes`() {
        val file = writeTrimmed(
            "conflicting-note.xcsv",
            """
            !section blends
            name,componentCompound,componentVariant,componentDose,note
            blendA,foo,fa,1,note A
            blendA,bar,ba,2,note B
            """
        )

        val ex = assertThrows<IllegalArgumentException> { loadData(file) }
        assertTrue(ex.message!!.contains("unexpected note value"))
    }

    @Test
    fun `convertHyphenMarkerToEmptyString`() {
        assertNull(null.convertHyphenMarkerToEmptyString())
        assertNull("".convertHyphenMarkerToEmptyString())
        assertEquals("", "-".convertHyphenMarkerToEmptyString())
        assertEquals("note", "note".convertHyphenMarkerToEmptyString())
    }
}
