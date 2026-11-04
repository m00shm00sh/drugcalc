package com.moshy.drugcalc.cmdclient

import com.moshy.drugcalc.cmdclient.states.ForConnect
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.types.login.UserRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days

internal class AppStateTest {
    @Test
    fun `fromConfig wires data state back to the app`() {
        val state = AppState().fromConfig()

        assertSame(state, state.forData.app!!.get())
    }

    @Test
    fun `config round trips through json`() {
        val state = AppState().apply {
            pageSize = 42
            forConnect.connect = ForConnect.ConnectParams("http://localhost:9999", insecure = true)
            forLogin.login = UserRequest("user", "pass")
            forData.newEntries = Data(
                compounds = mapOf(CompoundName("foo") to CompoundInfo(1.days))
            )
            forCalc.lineDurationInDays = 2.5
        }

        val json = Json.encodeToString(state)
        val restored = Json.decodeFromString<AppState>(json)

        assertEquals(42, restored.pageSize)
        assertEquals("http://localhost:9999", restored.forConnect.connect.addr)
        assertTrue(restored.forConnect.connect.insecure)
        assertEquals(UserRequest("user", "pass"), restored.forLogin.login)
        assertEquals(state.forData.newEntries, restored.forData.newEntries)
        assertEquals(2.5, restored.forCalc.lineDurationInDays)
    }
}
