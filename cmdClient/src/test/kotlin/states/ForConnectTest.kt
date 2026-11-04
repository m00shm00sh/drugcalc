package com.moshy.drugcalc.cmdclient.states

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class ForConnectTest {
    @Test
    fun `default connect params target localhost`() {
        val params = ForConnect.ConnectParams()

        assertEquals("http://127.0.0.1:8080", params.addr)
        assertFalse(params.insecure)
    }

    @Test
    fun `makeConnectableUrl joins the address and path`() {
        val params = ForConnect.ConnectParams("http://example:1234")

        assertEquals("http://example:1234/api", params.makeConnectableUrl("api"))
    }
}
