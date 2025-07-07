package com.moshy.drugcalc.server.http

import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_ALL
import com.moshy.drugcalc.server.http.routing.util.UrlStringSerializer.Companion.encode
import com.moshy.drugcalc.server.http.util.*
import com.moshy.ProxyMap
import com.moshy.drugcalc.commontest.CheckArg
import com.moshy.drugcalc.commontest.assertGreater
import com.moshy.drugcalc.commontest.assertSetsAreEqual
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.*
import kotlin.time.times

internal class CalcApiEndpointsTest {
    @MethodSource("casesForTestCycle")
    @ParameterizedTest(name = "{0}")
    fun `test cycle (GET)`(name: String, c: CheckArg<DecodedCycleResult, CycleRequest>) = withServer(RO_ALL) {
        c.invokeSuspend c@ {
            val q = buildString { encodeRequest(this@c) }
            testEndpoint<Unit, DecodedCycleResult>(Method.GetJson, "/api/calc?$q") {}
        }
    }

    @MethodSource("casesForTestCycle")
    @ParameterizedTest(name = "{0}")
    fun `test cycle (POST)`(name: String, c: CheckArg<DecodedCycleResult, CycleRequest>) = withServer(RO_ALL) {
        c.invokeSuspend c@ {
            testEndpoint<CycleRequest, DecodedCycleResult>(Method.PostJson, "/api/calc", reqBody = this@c)
        }
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

        val cycle = listOf(
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
        val q = buildString {
            encodeRequest(CycleRequest(
                config = ProxyMap(
                    "tickDuration" to 99 * DAY,
                    "cutoffMilligrams" to 999.999,
                    "doLambdaDoseCorrection" to true,
                ),
                data = data,
                cycle = cycle
            ))
        }

        val expectedResult = CycleRequest(
            data = data,
            config = ProxyMap(
                "tickDuration" to 99 * DAY,
                "cutoffMilligrams" to 999.999,
                "doLambdaDoseCorrection" to true,
            ),
            cycle = cycle
        )

        testEndpoint<Unit, CycleRequest>(Method.GetJson, "/api/calc/-test-c-/decode?$q") {
            assertEquals(expectedResult, this)
        }
    }

    companion object {
        @JvmStatic
        fun casesForTestCycle(): List<Arguments> {
            val DAY = 1.toDuration(DurationUnit.DAYS)
            val tick = 0.25 * DAY
            return buildList {
                add(Arguments.of("compound+transformer",
                    CheckArg.nothrow<DecodedCycleResult, _>(CycleRequest(
                                config = ProxyMap<Config>("tickDuration" to tick),
                                cycle = listOf(
                                    CycleDescription(
                                        compoundOrBlend = "anavar", dose = 100.0,
                                        start = 0 * DAY, duration = 7 * DAY,
                                        freqName = FrequencyName("every day")
                                    ),
                                    CycleDescription(
                                        prefix = CycleDescription.PREFIX_TRANSFORMER,
                                        compoundOrBlend = "anavar", variantOrTransformer = "median",
                                        start = DAY, duration = DAY * 6,
                                        freqName = FrequencyName("every other day")
                                    )
                                )
                            )
                    ) {
                        assertSetsAreEqual(setOf("anavar", "anavar:median"), it.keys)
                        val v = it["anavar"]!!
                        assertTrue(v.x.asSequence().zipWithNext { a, b -> (b - a) == tick }.all { it })
                        assertTrue(v.type == XYList.PlotType.POINT)
                        assertGreater(v.y[0], v.y[4])
                        val vm = it["anavar:median"]!!
                        assertTrue(vm.x.asSequence().zipWithNext { a, b -> (b - a) == 2 * DAY }.all { it })
                        assertTrue(vm.type == XYList.PlotType.BAR)
                    }
                ))
            }
        }
    }
}

private fun StringBuilder.encodeRequest(r: CycleRequest) {
    r.data?.let { addQueryKV("data", r.data.encodeJson(), true) }
    r.config?.let {
        for ((k, v) in it) {
            val qk = when (k) {
                "tickDuration" -> "tick"
                "cutoffMilligrams" -> "cutoff"
                "doLambdaDoseCorrection" -> "lambdacorrection"
                else -> error("unexpected: $k")
            }
            check(v !is List<*>) { "unsupported: list" }
            // TODO: we don't have special cases that need encoding (yet?)
            addQueryKV(qk, v)
        }
    }
    encodeCycleDescriptions(r.cycle)
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

private fun StringBuilder.encodeCycleDescriptions(l: List<CycleDescription>) {
    addQueryKVList("fl", l.map {
        when (it.prefix) {
            CycleDescription.PREFIX_COMPOUND -> ""
            CycleDescription.PREFIX_BLEND -> "b"
            CycleDescription.PREFIX_TRANSFORMER -> "t"
            else -> error("unexpected prefix ${it.prefix}")
        }
    })
    addQueryKVList("cb", l.map(CycleDescription::compoundOrBlend), encode = true)
    addQueryKVList("vx", l.map(CycleDescription::variantOrTransformer))
    addQueryKVList("d", l.map(CycleDescription::dose))
    addQueryKVList("s", l.map(CycleDescription::start))
    addQueryKVList("t", l.map(CycleDescription::duration))
    addQueryKVList("fn", l.map { it.freqName.value })
}