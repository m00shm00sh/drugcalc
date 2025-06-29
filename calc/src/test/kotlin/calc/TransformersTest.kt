package com.moshy.drugcalc.calc.calc

import com.moshy.drugcalc.commontest.CheckArg
import com.moshy.drugcalc.types.dataentry.CompoundBase
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.*

internal class TransformersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForTestTransformer")
    fun testTransformer(which: String, spec: String, a: CheckArg<TransformerResult, Unit>) =
        a.invoke { checkNotNull(transformerEntries[which]?.evaluator).invoke(allVals, spec, start, duration, freqs) }

    private companion object {
        val _4321: List<Double> = (4 downTo 1).map { it.toDouble() }
        val allVals: Map<CompoundBase, List<Double>> = mapOf(
            CompoundBase("a") to _4321 + _4321.map { it + 0.25 } + _4321
        )
        const val start = 0
        const val duration = 12
        val freqs = listOf(4)

        @JvmStatic
        fun testArgsForTestTransformer() =
            listOf(
                arrayOf("median",
                    "a",
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
private typealias TransformerResult = List<RangeValue<Double, Int>>
