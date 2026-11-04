package com.moshy.drugcalc.cmdclient.states

import com.moshy.drugcalc.cmdclient.io.data.JsonWithLenientIsoDuration
import com.moshy.drugcalc.types.dataentry.*
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.days

internal class ForDataTest {
    private val data = Data(
        compounds = mapOf(
            CompoundName("foo") to CompoundInfo(1.days)
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

    @Test
    fun `serializer round trips newEntries`() {
        val original = ForData(data)

        val json = JsonWithLenientIsoDuration.encodeToString(original)
        val restored = JsonWithLenientIsoDuration.decodeFromString<ForData>(json)

        assertEquals(data, restored.newEntries)
    }

    @Test
    fun `compounds section produces compound names`() {
        val section = ForData.CurrentSection.Compounds()

        assertEquals(CompoundName("foo"), section.produceName(listOf("foo")))
        assertEquals(
            CompoundName(CompoundBase("foo"), "v1"),
            section.produceName(listOf("foo", "v1"))
        )
    }

    @Test
    fun `compounds section requires a base name`() {
        val section = ForData.CurrentSection.Compounds()

        assertThrows<IllegalArgumentException> {
            section.produceName(emptyList())
        }
    }

    @Test
    fun `blends and frequencies sections produce their names`() {
        assertEquals(
            BlendName("mix"),
            ForData.CurrentSection.Blends().produceName(listOf("mix"))
        )
        assertEquals(
            FrequencyName("f1"),
            ForData.CurrentSection.Frequencies().produceName(listOf("f1"))
        )
    }

    @Test
    fun `mapAll visits compounds blends and frequencies in order`() {
        val visited = data.mapAll { it }

        assertEquals(listOf(data.compounds, data.blends, data.frequencies), visited)
    }
}
