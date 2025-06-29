package com.moshy.drugcalc.server.http

import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_ALL
import com.moshy.drugcalc.server.http.routing.util.UrlStringSerializer.Companion.encode
import com.moshy.drugcalc.server.http.util.*
import com.moshy.ProxyMap
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.*

internal class CalcApiEndpointsTest {
    @Test
    fun `test cycle (GET)`() = withServer(RO_ALL) {
        TODO()
    }

    @Test
    fun `test decode`() = withServer(RO_ALL) {
        val DAY = 1.toDuration(DurationUnit.DAYS)
        val data = Data(
            compounds = mapOf(
                CompoundName("j&/") to CompoundInfo(9 * DAY, 0.5)
            ),
            blends = mapOf(
                BlendName("k&/") to BlendValue(mapOf(
                    CompoundName("l") to 1.0,
                    CompoundName("m") to 2.0,
                ))
            ),
            frequencies = mapOf(
                FrequencyName("n&/") to FrequencyValue(listOf(2 * DAY, 3 * DAY))
            )
        )

        val q = buildString {
            addQueryKV("tick", 99 * DAY)
            addQueryKV("cutoff", 999.999)
            addQueryKV("lambdacorrection", true)

            addQueryKV("data", data.encodeJson(), true)

            addQueryKVList("fl", listOf("", "", "b", "t"))
            addQueryKVList("cb", listOf("a a", "bb", "cc", "dd"), true)
            addQueryKVList("vx", listOf("", "ee", "", "median"))
            addQueryKVList("d", listOf(1.0, 2.0, 3.0, null))
            addQueryKVList("s", listOf(DAY, 2 * DAY, 3 * DAY, 4 * DAY))
            addQueryKVList("t", listOf(5 * DAY, 6 * DAY, 7 * DAY, 8 * DAY))
            addQueryKVList("fn", listOf("ff", "gg", "hh", "ii"))
        }

        val expectedResult = CycleRequest(
            data = data,
            config = ProxyMap(
                "tickDuration" to 99 * DAY,
                "cutoffMilligrams" to 999.999,
                "doLambdaDoseCorrection" to true,
            ),
            cycle = listOf(
                CycleDescription(CycleDescription.PREFIX_COMPOUND, "a a", "", 1.0,
                    DAY, 5 * DAY, FrequencyName("ff")
                ),
                CycleDescription(CycleDescription.PREFIX_COMPOUND, "bb", "ee", 2.0,
                    2 * DAY, 6 * DAY, FrequencyName("gg")
                ),
                CycleDescription(CycleDescription.PREFIX_BLEND, "cc", "", 3.0,
                    3 * DAY, 7 * DAY, FrequencyName("hh")
                ),
                CycleDescription(CycleDescription.PREFIX_TRANSFORMER, "dd", "median", null,
                        4 * DAY, 8 * DAY, FrequencyName("ii")
                )
            )
        )

        testEndpoint<Unit, CycleRequest>(Method.GetJson, "/api/calc/-test-c-/decode?$q") {
            assertEquals(expectedResult, this)
        }

    }
}

private inline fun <reified V> V.encodeJson() = Json.encodeToString(this)

private fun <V> V?.toString_() = this?.toString() ?: ""

private fun <V> StringBuilder.addQueryKV(key: String, value: V?, encode: Boolean = false) {
    if (!isEmpty())
        append('&')
    append(key)
    append('=')
    append(value.toString_().run { if (encode) encode() else this })
}
private fun <V> StringBuilder.addQueryKVList(key: String, value: List<V?>, encode: Boolean = false) {
    if (!isEmpty())
        append('&')
    append(key)
    append('=')
    append(value.joinToString(separator = ",") { it.toString_().run { if (encode) encode() else this } })
}