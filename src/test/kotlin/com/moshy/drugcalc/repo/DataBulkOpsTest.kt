package com.moshy.drugcalc.repo

import com.moshy.drugcalc.calc.Config
import com.moshy.drugcalc.calcdata.*
import com.moshy.drugcalc.calcdata.BlendValue
import com.moshy.drugcalc.calcdata.BlendsMap
import com.moshy.drugcalc.calcdata.CompoundsMap
import com.moshy.drugcalc.calcdata.FrequenciesMap
import com.moshy.drugcalc.io.Diff
import com.moshy.drugcalc.io.DiffData
import com.moshy.drugcalc.io.FullData
import com.moshy.drugcalc.testutil.CheckArg
import com.moshy.drugcalc.testutil.assertSetsAreEqual
import com.moshy.ProxyMap
import com.moshy.drugcalc.misc.fillActiveCompounds

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)

// two of the compounds have the same base to test that reducing on the base compound name functions
private val compound1 = "foo bar" to CompoundInfo(ONE_DAY * 2, 0.75)
private val compound2 = "bar baz" to CompoundInfo(ONE_DAY, 0.9)
private val compound3 = "foo baz" to CompoundInfo(ONE_DAY / 2, 1.0)
private val _compounds: CompoundsMap = mapOf(compound1, compound2, compound3).fillActiveCompounds()
private val blend1: Pair<String, BlendValue> = "fred" to mapOf(
    "foo bar" to 100.0,
    "foo baz" to 50.0
)
private val blend2 = "${blend1.first} derf" to blend1.second.mapValues { (_, v) -> 150.0 - v }
private val _blends: BlendsMap = mapOf(blend1, blend2)
private val freq1 = "f1" to listOf(ONE_DAY)
private val freq2 = "f2" to listOf(ONE_DAY * 2)
private val freq3 = "f_1_2" to listOf(ONE_DAY, ONE_DAY * 2)
private val _freqs: FrequenciesMap = mapOf(freq1, freq2)

internal class DataBulkOpsTest {
    internal class DSApplyDataP(
        val ds: DataStore = DataStore(),
        val fd: FullData = FullData()
    )
    private companion object {
        val compound4 = "aaaaa" to CompoundInfo(ONE_DAY, activeCompound = "aaaaa")
        val blend3 = "${blend1.first} drf" to blend1.second.mapValues { (_, v) -> v * 2.0 }

        val origDS = DataStore().apply {
            addCompounds(_compounds)
            addBlends(_blends)
            addFrequencies(_freqs)
        }

        /* DataStore.applyDiff has two execution forms:
         * (1) fail, leaving original object untouched,
         *     (a) for each of:
         *         (i) cycle detected
         *         (ii) failed delete
         *         (iii) failed add
         *     (b) and for each case of:
         *         (i) compounds
         *         (ii) blends
         *         (iii) frequencies
         * (2) succeed and return copy
         *
         * We do not check for casefold collision; that is the job of DiffData.caseFolded().
         */
        @JvmStatic
        fun testArgsForApplyDiff(): List<Arguments> {
            val compound1 = compound1.run { mapOf(this) }.fillActiveCompounds().entries.first().toPair()
            val values = mapOf(
                "compounds" to compound1,
                "blends" to blend1,
                "frequencies" to freq1
            )
            @Suppress("UNCHECKED_CAST") val diffDataOf = mapOf<String, (Diff<*>) -> DiffData>(
                "compounds" to { DiffData(compounds = it as Diff<CompoundInfo>) },
                "blends" to { DiffData(blends = it as Diff<BlendValue>) },
                "frequencies" to { DiffData(frequencies = it as Diff<FrequencyValue>) }
            )
            val cbfSingular = mapOf(
                "compounds" to "compound",
                "blends" to "blend",
                "frequencies" to "frequency"
            )

            val fails = listOf("compounds", "blends", "frequencies").flatMap {
                val dataVals = values[it]!!
                val dvName = dataVals.first
                val diffDataFn = diffDataOf[it]!!
                // list(array( name: String, diffData: DiffData, exceptionMsg: String ))
                listOf(
                    arrayOf("delete $it",
                        diffDataFn(Diff<Any>(delete = setOf("${dvName}_abc"))),
                        "missing ${cbfSingular[it]} \"${dvName}_abc\""
                    ),
                    arrayOf("add $it",
                        diffDataFn(Diff(add = mapOf(dataVals))),
                        "$it: \"$dvName\" already exists"
                    )
                )
            }.map {
                Arguments.of(it[0],
                    CheckArg.throws<IllegalArgumentException, DiffData>(it[1] as DiffData, it[2] as String))
            }
            val successes = listOf(
                Arguments.of("blend cyclical is ok when compound value changes",
                    CheckArg.nothrow(DiffData(
                        compounds = Diff(
                            delete = setOf(compound1.first),
                            add = mapOf(compound1.first to compound1.second.copy(pctActive = 1.0))
                        ),
                        blends = Diff(
                            delete = _blends.keys,
                            add = _blends
                        )
                    ))
                ),
                Arguments.of("success",
                    CheckArg.nothrow<DataStore, _>(DiffData(
                        compounds = Diff(
                            delete = setOf(
                                compound1.first,
                                compound2.first
                            ),
                            add = mapOf(
                                compound1.first to compound1.second.copy(pctActive = 1.0),
                                compound4
                            )
                        ),
                        blends = Diff(
                            delete = setOf(
                                blend1.first,
                                blend2.first
                            ),
                            add = mapOf(
                                blend1.first to blend1.second.mapValues { (_, dose) -> dose * 3.0 },
                                blend3
                            )
                        ),
                        frequencies = Diff(
                            delete = setOf(
                                freq1.first,
                                freq2.first,
                            ),
                            add = mapOf(
                                freq1.first to freq1.second.map { it * 2 },
                                freq3
                            )
                        )
                    )) { ds ->
                        val cKeys = ds.compounds.keys
                        val bKeys = ds.blends.keys
                        val fKeys = ds.frequencies.keys
                        val expCKeys = listOf(compound1, compound3, compound4).map { it.first }.toSet()
                        val expBKeys = listOf(blend1, blend3).map { it.first }.toSet()
                        val expFKeys = listOf(freq1, freq3).map { it.first }.toSet()
                        assertAll(
                            { assertSetsAreEqual(expCKeys, cKeys,"compounds") },
                            { assertSetsAreEqual(expBKeys, bKeys, "blends") },
                            { assertSetsAreEqual(expFKeys, fKeys, "frequencies") }
                        )
                    }
                )
            )

            return listOf(fails, successes).flatten()
        }


        /* DataStore().applyData has three execution forms:
         * (1) applyData to non-empty DS -> exception
         * (2) applyData with malformed diff -> exception
         * (3) applyData to empty DS with valid diff -> new DS
         */
          @JvmStatic
          fun testArgsForApplyData() =
              listOf(
                  arrayOf("non-empty DS",
                      CheckArg.throws<IllegalArgumentException, _>(
                          DSApplyDataP(ds = DataStore().apply { addFrequencies(mapOf(freq1)) },),
                          "attempted applyData on non-empty DataStore"
                      ),
                  ),
                  arrayOf("bad diff",
                      CheckArg.throws<IllegalArgumentException, _>(
                          DSApplyDataP(fd = FullData(blends = mapOf(":" to mapOf(":" to 0.0)))),
                          "blend"
                      )
                  ),
                  arrayOf("pass",
                      CheckArg.nothrow<DataStore, _>(
                          DSApplyDataP(fd = FullData(compounds = _compounds, blends = _blends, frequencies = _freqs))
                      ) {
                          assertAll(
                              { assertSetsAreEqual(_compounds.keys, it.compounds.keys, "for compounds") },
                              { assertSetsAreEqual(_blends.keys, it.blends.keys, "for blends") },
                              { assertSetsAreEqual(_freqs.keys, it.frequencies.keys, "for frequencies") }
                          )
                      }
                  )
              )
                  .map { Arguments.of(*it) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForApplyDiff")
    fun applyDiff(name: String, a: CheckArg<DataStore, DiffData>) =
        a.invoke { origDS.buildCopy().applyDiff(this.toFullDiffData(origDS)) }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForApplyData")
    fun testApplyData(name: String, a: CheckArg<DataStore, DSApplyDataP>) =
        a.invoke { ds.applyData(fd) }

    @Test
    fun testApplyLens() {
        val c = Config(ONE_DAY, 0.1, false)
        val lens = ProxyMap<Config>(mapOf("cutoff" to 1.0))
        assertEquals(1.0, c.applyLens(lens).cutoff)
    }
}