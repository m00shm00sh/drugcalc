package com.moshy.drugcalc.misc

import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.io.*
import com.moshy.drugcalc.io.Diff
import com.moshy.drugcalc.io.DiffData
import com.moshy.drugcalc.io.FullDiff
import com.moshy.drugcalc.io.FullDiffData
import com.moshy.drugcalc.testutil.CheckArg
import com.moshy.drugcalc.testutil.assertSetsAreEqual
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.DurationUnit
import kotlin.time.times
import kotlin.time.toDuration

internal class DiffUtilsTest {

    companion object {
        val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)

        @JvmStatic
        fun testArgsForCaseFold() =
            listOf(
                arrayOf("compounds del collision",
                    CheckArg.throws<IllegalArgumentException, _>(DiffData(compounds = Diff(delete = setOf("a", "A"))),
                        "compounds.delete: case folding produced a duplicate key for \"A\"")
                ),
                arrayOf("compounds add collision",
                    CheckArg.throws<IllegalArgumentException, _>(
                        DiffData(compounds = Diff(add = setOf("b", "B").associateWith { CompoundInfo(ONE_DAY) })),
                        "compounds.add: case folding produced a duplicate key for \"B\"")
                ),
                arrayOf("blends del collision",
                    CheckArg.throws<IllegalArgumentException, _>(DiffData(blends = Diff(delete = setOf("c", "C"))),
                        "blends.delete: case folding produced a duplicate key for \"C\"")
                ),
                arrayOf("blends add collision",
                    CheckArg.throws<IllegalArgumentException, _>(
                        DiffData(blends = Diff(add = setOf("d", "D").associateWith { emptyMap() })),
                        "blends.add: case folding produced a duplicate key for \"D\"")
                ),
                arrayOf("freqs del collision",
                    CheckArg.throws<IllegalArgumentException, _>(DiffData(frequencies = Diff(delete = setOf("e", "E"))),
                        "frequencies.delete: case folding produced a duplicate key for \"E\"")
                ),
                arrayOf("freqs add collision",
                    CheckArg.throws<IllegalArgumentException, _>(
                        DiffData(frequencies = Diff(add = setOf("f", "F").associateWith { emptyList() })),
                        "frequencies.add: case folding produced a duplicate key for \"F\"")
                ),
                arrayOf("ok",
                    CheckArg.nothrow<DiffData, _>(
                        DiffData(
                            compounds = Diff(delete = setOf("A"), add = setOf("B").associateWith { CompoundInfo(ONE_DAY) }),
                            blends = Diff(delete = setOf("C"), add = setOf("D").associateWith { emptyMap() }),
                            frequencies = Diff(delete = setOf("E"), add = setOf("F").associateWith { emptyList() })
                        )
                    ) {
                        assertAll(
                            { assertSetsAreEqual(setOf("a"), it.compounds.delete.toSet()) },
                            { assertSetsAreEqual(setOf("b"), it.compounds.add.keys) },
                            { assertSetsAreEqual(setOf("c"), it.blends.delete.toSet()) },
                            { assertSetsAreEqual(setOf("d"), it.blends.add.keys) },
                            { assertSetsAreEqual(setOf("e"), it.frequencies.delete.toSet()) },
                            { assertSetsAreEqual(setOf("f"), it.frequencies.add.keys) },
                        )
                    }
                )
            ).map { Arguments.of(*it) }
    }

    /* the logic of FullDiff<V>.combineWith(FullDiff<V>) is tested here;
     * testApplyDiff tests Map<String, V>.applyDiff(FullDiff<V>)
     */
    @Test
    fun testFDDComputeCompositeDiff() {
        /* A composite diff should have the following things for each of (compounds, blends, frequencies):
         * (1) value removed (a)
         * (2) value added   (d)
         * (3) value added and then removed -> missing (c)
         * (4) value modified and then re-modified (b, b2, b3)
         * (1), (2), and (3) all require one item; (4) requires three.
         */
        val ca = mapOf("a" to CompoundInfo(ONE_DAY))
        val cb = mapOf("b" to CompoundInfo(ONE_DAY))
        val cb2 = mapOf("b" to CompoundInfo(ONE_DAY * 2))
        val cb3 = mapOf("b" to CompoundInfo(ONE_DAY * 3))
        val cc = mapOf("c" to CompoundInfo(ONE_DAY))
        val cd = mapOf("d" to CompoundInfo(ONE_DAY))
        val ba = mapOf("a" to mapOf("a" to 1.0))
        val bb = mapOf("b" to mapOf("a" to 1.0))
        val bb2 = mapOf("b" to mapOf("b" to 1.0))
        val bb3 = mapOf("b" to mapOf("c" to 1.0))
        val bc = mapOf("c" to mapOf("a" to 1.0))
        val bd = mapOf("d" to mapOf("a" to 1.0))
        val fa = mapOf("a" to listOf(ONE_DAY))
        val fb = mapOf("b" to listOf(ONE_DAY))
        val fb2 = mapOf("b" to listOf(ONE_DAY * 2))
        val fb3 = mapOf("b" to listOf(ONE_DAY * 3))
        val fc = mapOf("c" to listOf(ONE_DAY))
        val fd = mapOf("d" to listOf(ONE_DAY))
        val fdds = listOf(
            FullDiffData(
                compounds = FullDiff(
                    delete = ca + cb,
                    add = cb2 + cc
                ),
                blends = FullDiff(
                    delete = ba + bb,
                    add = bb2 + bc
                ),
                frequencies = FullDiff(
                    delete = fa + fb,
                    add = fb2 + fc
                )
            ),
            FullDiffData(
                compounds = FullDiff(
                    delete = cb2 + cc,
                    add = cb3 + cd
                ),
                blends = FullDiff(
                    delete = bb2 + bc,
                    add = bb3 + bd
                ),
                frequencies = FullDiff(
                    delete = fb2 + fc,
                    add = fb3 + fd
                )
            )
        )
        val exp = FullDiffData(
            compounds = FullDiff(
                delete = ca + cb,
                add = cb3 + cd
            ),
            blends = FullDiff(
                delete = ba + bb,
                add = bb3 + bd
            ),
            frequencies = FullDiff(
                delete = fa + fb,
                add = fb3 + fd
            )
        )
        val got = fdds.computeCompositeDiff()

        assertEquals(exp, got)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForCaseFold")
    fun testDDCaseFold(name: String, a: CheckArg<DiffData, DiffData>) =
        a.invoke(DiffData::caseFolded)

    @Test
    fun testDDWithPrepareCompounds() {
        val dd = DiffData(
            compounds = Diff(
                delete = setOf("a b"),
                add = mapOf("a b" to CompoundInfo(ONE_DAY), "a b c" to CompoundInfo(ONE_DAY))
            ),
            blends = Diff(
                delete = setOf("a b"),
                add = mapOf("a b" to mapOf("a b" to 1.0))
            ),
            frequencies = Diff(
                delete = setOf("a b"),
                add = mapOf("a b" to listOf(ONE_DAY))
            )
        )
        val got = dd.withPreparedCompounds()
        val exp = dd.copy(
            compounds = dd.compounds.copy(
                add = mapOf(
                    "a b" to CompoundInfo(ONE_DAY, activeCompound = "a"),
                    "a b c" to CompoundInfo(ONE_DAY, activeCompound = "a b")
                )
            )
        )
        assertEquals(exp, got)
    }

    @Test
    fun testFDSummarized() {
        val fd = FullDiff(
            delete = mapOf("a" to emptyMap(), "b" to emptyMap()),
            add = mapOf("b" to mapOf("a" to 1.0), "c" to emptyMap())
        )
        val exp = 2 to 2
        val got = fd.summarized()
        assertEquals(exp, got)
    }

    @Test
    fun testFDDReversed() {
        val data = FullDiffData(
            compounds = FullDiff(
                delete = mapOf("a" to CompoundInfo(ONE_DAY)),
                add = mapOf("b" to CompoundInfo(2 * ONE_DAY))
            ),
            blends = FullDiff(
                delete = mapOf("a" to emptyMap()),
                add = mapOf("b" to mapOf("a" to 1.0))
            ),
            frequencies = FullDiff(
                delete = mapOf("a" to emptyList()),
                add = mapOf("b" to listOf(ONE_DAY))
            )
        )
        val exp = FullDiffData(
            compounds = FullDiff(
                delete = mapOf("b" to CompoundInfo(2 * ONE_DAY)),
                add = mapOf("a" to CompoundInfo(ONE_DAY))
            ),
            blends = FullDiff(
                delete = mapOf("b" to mapOf("a" to 1.0)),
                add = mapOf("a" to emptyMap())
            ),
            frequencies = FullDiff(
                delete = mapOf("b" to listOf(ONE_DAY)),
                add = mapOf("a" to emptyList())
            )
        )
        val got = data.reversed()
        assertEquals(exp, got)
    }

    @Test
    fun testFDApplyDiff() {
        val data = FullData(
            compounds = mapOf("a" to CompoundInfo(ONE_DAY), "b" to CompoundInfo(ONE_DAY)),
            blends = mapOf("a" to emptyMap(), "b" to emptyMap()),
            frequencies = mapOf("a" to emptyList(), "b" to emptyList())
        )
        val diff = FullDiffData(
            compounds = FullDiff(
                delete = mapOf("a" to CompoundInfo(ONE_DAY), "b" to CompoundInfo(ONE_DAY)),
                add = mapOf("b" to CompoundInfo(2 * ONE_DAY), "c" to CompoundInfo(ONE_DAY))
            ),
            blends = FullDiff(
                delete = mapOf("a" to emptyMap(), "b" to emptyMap()),
                add = mapOf("b" to mapOf("a" to 1.0), "c" to emptyMap())
            ),
            frequencies = FullDiff(
                delete = mapOf("a" to emptyList(), "b" to emptyList()),
                add = mapOf("b" to listOf(ONE_DAY), "c" to emptyList())
            )
        )
        val got = data.applyDiff(diff)
        val exp = FullData(
            compounds = mapOf("b" to CompoundInfo(2 * ONE_DAY), "c" to CompoundInfo(ONE_DAY)),
            blends = mapOf("b" to mapOf("a" to 1.0), "c" to emptyMap()),
            frequencies = mapOf("b" to listOf(ONE_DAY), "c" to emptyList())
        )
        assertEquals(exp, got)
    }
}