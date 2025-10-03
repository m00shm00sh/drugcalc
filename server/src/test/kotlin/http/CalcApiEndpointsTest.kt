package com.moshy.drugcalc.server.http

import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_ALL
import com.moshy.drugcalc.server.http.routing.util.UrlStringSerializer.Companion.encode
import com.moshy.drugcalc.server.http.util.*
import com.moshy.ProxyMap
import com.moshy.drugcalc.common.runIf
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
    fun `test cycle (POST)`(name: String, c: CheckArg<CycleResult<XYList>, CycleRequest>) = withServer(RO_ALL) {
        c.invokeSuspend c@ {
            testEndpoint<CycleRequest, CycleResult<XYList>>(Method.PostJson, "/api/calc", reqBody = this@c)
        }
    }

    companion object {
        @JvmStatic
        fun casesForTestCycle(): List<Arguments> {
            val DAY = 1.toDuration(DurationUnit.DAYS)
            val tick = 0.25 * DAY
            return buildList {
                add(Arguments.of("compound+transformer",
                    CheckArg.nothrow<CycleResult<XYList>, _>(CycleRequest(
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
                    ) { xyl ->
                        val it = requireNotNull(xyl.refineOrNull<XYList.OfDuration>()) {
                            "unexpected XYList type"
                        }
                        assertSetsAreEqual(setOf("anavar", "anavar:median"), it.keys)
                        val v = it["anavar"]!!
                        assertTrue(v.x.asSequence().zipWithNext { a, b -> (b - a) == tick }.all { it })
                        assertTrue(v.plotType == XYList.PlotType.POINT)
                        assertGreater(v.y[0], v.y[4])
                        val vm = it["anavar:median"]!!
                        assertTrue(vm.x.asSequence().zipWithNext { a, b -> (b - a) == 2 * DAY }.all { it })
                        assertTrue(vm.plotType == XYList.PlotType.BAR)
                    }
                ))
            }
        }
    }
}
