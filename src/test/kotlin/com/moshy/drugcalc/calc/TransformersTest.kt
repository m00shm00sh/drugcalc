package com.moshy.drugcalc.calc

import com.moshy.drugcalc.internaltypes.RangeValue
import com.moshy.drugcalc.testutil.CheckArg
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class TransformersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForTestTransformer")
    fun testTransformer(which: String, a: CheckArg<TransformerResult, Unit>) =
        a.invoke { checkNotNull(transformers[which]?.second).invoke(vals, start, duration, freqs) }

    private companion object {
        val _4321: @TimeTickIndexed List<Double> = (4 downTo 1).map { it.toDouble() }
        val vals: @TimeTickIndexed List<Double> = _4321 + _4321.map { it + 0.25 } + _4321
        val start = 0
        val duration = 12
        val freqs = listOf(4)

        @JvmStatic
        fun testArgsForTestTransformer() =
            listOf(
                arrayOf("median",
                    CheckArg.nothrow<TransformerResult, Unit>(Unit)
                    {
                        assertAll(
                            { assertTrue(it.size == 3) },
                            { assertTrue(it[0].value == 2.5) },
                            { assertTrue(it[1].value == 2.75) },
                            { assertTrue(it[2].value == 2.5) }
                        )
                    }
                )
            ).map { Arguments.of(*it) }
    }
}
private typealias TransformerResult = List<RangeValue<Double, @TimeTickScaled Int>>
