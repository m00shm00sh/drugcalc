package com.moshy.drugcalc.cmdclient.io.data

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

internal class DurationSerializersTest {
    @Serializable
    private data class DurationHolder(@Contextual val value: Duration)

    @Test
    fun `deserializes lenient kotlin duration strings`() {
        val holder = JsonWithLenientIsoDuration.decodeFromString<DurationHolder>(
            """{"value":"1.5d"}"""
        )

        assertEquals(1.5.days, holder.value)
    }

    @Test
    fun `deserializes iso duration strings`() {
        val holder = JsonWithLenientIsoDuration.decodeFromString<DurationHolder>(
            """{"value":"PT90M"}"""
        )

        assertEquals(90.minutes, holder.value)
    }

    @Test
    fun `serializes durations as iso strings`() {
        val json = JsonWithLenientIsoDuration.encodeToString(DurationHolder(90.minutes))

        assertEquals("""{"value":"PT1H30M"}""", json)
    }

    @Test
    fun `ignores unknown keys`() {
        val holder = JsonWithLenientIsoDuration.decodeFromString<DurationHolder>(
            """{"value":"1d","extra":true}"""
        )

        assertEquals(1.days, holder.value)
    }
}
