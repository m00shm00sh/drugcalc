package com.moshy.drugcalc.cmdclient.io

import com.moshy.drugcalc.cmdclient.io.UrlStringSerializer.Companion.decode
import com.moshy.drugcalc.cmdclient.io.UrlStringSerializer.Companion.encode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class UrlStringTest {
    @Test
    fun `encode and decode round trip`() {
        val values = listOf(
            "plain",
            "a b",
            "a+b",
            "a/b?c=d&e",
            "a,b",
            "üñïçødé",
        )

        for (value in values)
            assertEquals(value, value.encode().decode())
    }

    @Test
    fun `encode uses plus for spaces`() {
        assertEquals("a+b", "a b".encode())
    }

    @Test
    fun `UrlString serializes as an encoded string`() {
        val original = UrlString("a b/c")

        val json = Json.encodeToString(original)

        assertEquals("\"a+b%2Fc\"", json)
        assertEquals(original, Json.decodeFromString<UrlString>(json))
    }

    @Test
    fun `UrlStringList round trips through a comma-separated string`() {
        val original = UrlStringList(listOf("a b", "c/d", "e,f"))

        val json = Json.encodeToString(original)
        val restored = Json.decodeFromString<UrlStringList>(json)

        assertEquals(original, restored)
    }
}
