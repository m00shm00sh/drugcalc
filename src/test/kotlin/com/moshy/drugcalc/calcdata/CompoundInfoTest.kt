package com.moshy.drugcalc.calcdata

import com.moshy.ProxyMap
import com.moshy.drugcalc.testutil.CheckArg
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/* use ProxyMap lensing to create the arg maps for test;
 * this permits checking of default values
 */
internal class CompoundInfoTest {
    @ParameterizedTest
    @MethodSource("argsForCompoundInfoCtorTest")
    fun test(a: CheckArg<Unit, Map<String, Any?>>) =
        a.invoke {
            ProxyMap<CompoundInfo>(this).applyToObject(required)
        }

    internal companion object {
        val required = CompoundInfo((1.0).toDuration(DurationUnit.DAYS))

        @JvmStatic
        fun argsForCompoundInfoCtorTest() =
            listOf(
                CheckArg.nothrow(emptyMap<String, Any?>()),
                CheckArg.nothrow(mapOf("pctActive" to 0.5)),
                CheckArg.throws<IllegalArgumentException, _>(mapOf("pctActive" to 0.0), "pctActive"),
                CheckArg.throws<IllegalArgumentException, _>(mapOf("pctActive" to 1.1), "pctActive"),
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("halfLife" to 0.toDuration(DurationUnit.DAYS)),
                    "halfLife"
                )
            )
            .map { Arguments.of(it) }
    }
}
