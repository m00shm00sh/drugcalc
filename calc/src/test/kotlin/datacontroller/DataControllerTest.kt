package com.moshy.drugcalc.calc.datacontroller

import com.moshy.drugcalc.types.calccommand.CycleDescription
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.types.datasource.*
import com.moshy.containers.*
import com.moshy.ProxyMap
import com.moshy.drugcalc.calctest.*
import com.moshy.drugcalc.calctest.DataControllerTestSupport.RoFlag
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_ALL
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_NONE
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.newTestController
import com.moshy.drugcalc.commontest.*
import kotlinx.coroutines.test.runTest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
// unstarred import so it takes precedence for assert(DoesNotThrow|Throws)
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.EnumSet

import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class DataControllerTest {
    @Test
    fun getCompoundNames() = runTestWithTempController(RO_ALL) {
        val actual = getCompoundNames()
        assertEquals(DataControllerTestSupport.expectedCompoundNames, actual)
    }

    @Test
    fun getBlendNames() = runTestWithTempController(RO_ALL) {
        val actual = getBlendNames()
        assertEquals(DataControllerTestSupport.expectedBlendNames, actual)
    }

    @Test
    fun getFrequencyNames() = runTestWithTempController(RO_ALL) {
        val actual = getFrequencyNames()
        assertEquals(DataControllerTestSupport.expectedFrequencyNames, actual)
    }

    // TODO: coverage +auxData
    @Test
    fun `resolveNamesForCycle basic`() = runTestWithTempController(RO_ALL) {
        val t1 = 1.toDuration(DurationUnit.DAYS)
        val cycle = listOf(
            CycleDescription(
                compoundOrBlend = "testosterone",
                variantOrTransformer = "enanthate",
                dose = 1.0,
                start = t1,
                duration = t1,
                freqName = FrequencyName("every day")
            ),
            CycleDescription(
                prefix = CycleDescription.PREFIX_BLEND,
                compoundOrBlend = "sustanon 250",
                dose = 1.0,
                start = t1,
                duration = t1,
                freqName = FrequencyName("every other day")
            ),
            CycleDescription(
                prefix = CycleDescription.PREFIX_TRANSFORMER,
                compoundOrBlend = "ligma",
                variantOrTransformer = "median",
                start = t1,
                duration = t1,
                freqName = FrequencyName("every week")
            )
        )
        val cNamesExpected = setOf(
            CompoundName(CompoundBase("testosterone"), "enanthate"),
            // blend expansion
            CompoundName(CompoundBase("testosterone"), "propionate"),
            CompoundName(CompoundBase("testosterone"), "phenylpropionate"),
            CompoundName(CompoundBase("testosterone"), "isocaproate"),
            CompoundName(CompoundBase("testosterone"), "decanoate"),

            )
        val bNames = setOf(
            BlendName("sustanon 250"),
        )
        val fNames = setOf(
            FrequencyName("every day"),
            FrequencyName("every other day"),
            FrequencyName("every week"),
        )
        val data = resolveNamesForCycle(cycle)
        assertAll(
            { assertEquals(cNamesExpected, data.compounds.keys) },
            { assertEquals(bNames, data.blends.keys) },
            { assertEquals(fNames, data.frequencies.keys) }
        )
    }

    @Test
    fun `resolveNames basic`() = runTestWithTempController(RO_ALL) {
        val cNames = setOf(
            CompoundName(CompoundBase("testosterone"), "enanthate"),
        )
        val cNamesExpected = setOf(
            CompoundName(CompoundBase("testosterone"), "enanthate"),
            // blend expansion
            CompoundName(CompoundBase("testosterone"), "propionate"),
            CompoundName(CompoundBase("testosterone"), "phenylpropionate"),
            CompoundName(CompoundBase("testosterone"), "isocaproate"),
            CompoundName(CompoundBase("testosterone"), "decanoate"),

            )
        val bNames = setOf(
            BlendName("sustanon 250"),
        )
        val fNames = setOf(
            FrequencyName("every day"),
            FrequencyName("every other day"),
        )
        val data = resolveNames(cNames, bNames, fNames)
        assertAll(
            { assertEquals(cNamesExpected, data.compounds.keys) },
            { assertEquals(bNames, data.blends.keys) },
            { assertEquals(fNames, data.frequencies.keys) }
        )
    }

    @Test
    fun `resolveNames expand`() = runTestWithTempController(RO_ALL) {
        val auxData = Data(
            blends = mapOf(
                BlendName("fred") to
                        BlendValue(mapOf(
                            CompoundName("waldo") to 1.0,
                            CompoundName("wally") to 1.0,
                        )),
            )
        )
        val bNames = setOf(BlendName("fred"))
        assertDoesNotThrow {
            resolveNames(
                cNames = DataController.Companion.DONT_EXPAND_BLEND_COMPOUNDS, bNames = bNames, auxData = auxData
            )
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "unresolved compounds: [CompoundName(waldo), CompoundName(wally)]"
        ).invokeSuspend { resolveNames(bNames = bNames, auxData = auxData) }
    }

    @Test
    fun `resolveNames f-missing`() = runTestWithTempController(RO_ALL) {
        val cNames = setOf(
            CompoundName("testosterone"),
        )
        val bNames = setOf(
            BlendName("waldo"),
        )
        val fNames = setOf(
            FrequencyName("waldo"),
        )
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "unresolved compounds: [CompoundName(testosterone)]"
        ).invokeSuspend { resolveNames(cNames = cNames) }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "unresolved blends: [waldo]"
        ).invokeSuspend { resolveNames(bNames = bNames) }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "unresolved frequencies: [waldo]"
        ).invokeSuspend { resolveNames(fNames = fNames) }
    }

    @Test
    fun `resolveNames +auxData`() = runTestWithTempController(RO_ALL) {
        val auxData = Data(
            compounds = mapOf(
                CompoundName(CompoundBase("hello"), "world") to
                        CompoundInfo(1.toDuration(DurationUnit.DAYS)),
                CompoundName("goodbye") to
                        CompoundInfo(2.toDuration(DurationUnit.DAYS)),
            ),
            blends = mapOf(
                BlendName("waldo") to
                        BlendValue(mapOf(
                            CompoundName(CompoundBase("hello"), "world") to 6.9,
                            CompoundName(CompoundBase("testosterone"), "cypionate") to 4.20,
                        ))
            ),
            frequencies = mapOf(
                FrequencyName("soon") to
                        FrequencyValue(listOf(1_000_000.toDuration(DurationUnit.DAYS)))
            )
        )
        val cNames = setOf(
            CompoundName(CompoundBase("testosterone"), "enanthate"),
            CompoundName("goodbye"),

        )
        val cNamesExpected = setOf(
            CompoundName(CompoundBase("testosterone"), "enanthate"),
            // blend expansion
            CompoundName(CompoundBase("testosterone"), "propionate"),
            CompoundName(CompoundBase("testosterone"), "phenylpropionate"),
            CompoundName(CompoundBase("testosterone"), "isocaproate"),
            CompoundName(CompoundBase("testosterone"), "decanoate"),
            // auxData compound
            CompoundName("goodbye"),
            // auxData blend expansion
            CompoundName(CompoundBase("hello"), "world"),
            CompoundName(CompoundBase("testosterone"), "cypionate") ,
        )
        val bNames = setOf(
            BlendName("sustanon 250"),
            // auxData
            BlendName("waldo"),
        )
        val fNames = setOf(
            FrequencyName("every day"),
            // auxData
            FrequencyName("soon")
        )
        val data = resolveNames(cNames, bNames, fNames, auxData)
        assertAll(
            { assertEquals(cNamesExpected, data.compounds.keys) },
            { assertEquals(bNames, data.blends.keys) },
            { assertEquals(fNames, data.frequencies.keys) }
        )
    }

    @Test
    fun `resolveNames +auxData override`() = runTestWithTempController(RO_ALL) {
        val auxData = Data(
            compounds = mapOf(
                CompoundName(CompoundBase("testosterone"), "propionate") to
                    CompoundInfo(99.toDuration(DurationUnit.DAYS)),
                CompoundName("waldo") to
                    CompoundInfo(123.toDuration(DurationUnit.DAYS))
            ),
            blends = mapOf(
                BlendName("sustanon 250") to
                        BlendValue(mapOf(
                            CompoundName("waldo") to 1.0,
                            CompoundName(CompoundBase("testosterone"), "propionate") to 1.0,
                            CompoundName(CompoundBase("testosterone"), "isocaproate") to 1.0,
                        ))
            ),
            frequencies = mapOf(
                FrequencyName("every day") to
                        FrequencyValue(listOf(1_000_000.toDuration(DurationUnit.DAYS)))
            )
        )
        val cNames = setOf(
            CompoundName("waldo"),

            )
        val cNamesExpected = setOf(
            CompoundName("waldo"),
            CompoundName(CompoundBase("testosterone"), "isocaproate"),
            CompoundName(CompoundBase("testosterone"), "propionate"),
        )
        val bNames = setOf(
            BlendName("sustanon 250"),
        )
        val fNames = setOf(
            FrequencyName("every day"),
        )
        val data = resolveNames(cNames, bNames, fNames, auxData)
        assertAll(
            { assertEquals(cNamesExpected, data.compounds.keys) },
            {
                assertEquals(99.toDuration(DurationUnit.DAYS),
                data.compounds[CompoundName(CompoundBase("testosterone"), "propionate")]!!.halfLife
                )
            },
            { assertEquals(bNames, data.blends.keys) },
            {
                assertEquals(
                    1.0,
                    data.blends[BlendName("sustanon 250")]!!.values.first()
                )
            },
            { assertEquals(fNames, data.frequencies.keys) },
            {
                assertEquals(1_000_000.toDuration(DurationUnit.DAYS),
                    data.frequencies[FrequencyName("every day")]!!.first()
                )
            }
        )
    }
    @Test
    fun `validate compounds consistency`() = runTestWithTempController(RO_ALL) {
        val t = 1.toDuration(DurationUnit.DAYS)
        val c1 = mapOf(
            CompoundName(CompoundBase("anavar"), "ham") to
                CompoundInfo(t)
        )
        val c2 = mapOf(
            CompoundName("testosterone") to
                CompoundInfo(t)
        )
        val c3 = mapOf(
            CompoundName("spam") to
                CompoundInfo(t),
            CompoundName(CompoundBase("spam"), "bacon") to
                CompoundInfo(t),
        )
        val c4 = mapOf(
            CompoundName(CompoundBase("spam"), "bacon") to
                CompoundInfo(t),
            CompoundName("spam") to
                CompoundInfo(t),
        )
        val cNamesDummy = setOf(CompoundName("bacon"))
        CheckArg.throws<IllegalArgumentException, _>(c1,
            "compound anavar requires no variant (got ham)"
        ).invokeSuspend { resolveNames(cNames = cNamesDummy, auxData = Data(compounds = this)) }
        CheckArg.throws<IllegalArgumentException, _>(c2,
            "compound testosterone requires a variant"
        ).invokeSuspend { resolveNames(cNames = cNamesDummy, auxData = Data(compounds = this)) }
        CheckArg.throws<IllegalArgumentException, _>(c3,
            "compound spam requires no variant (got bacon)"
        ).invokeSuspend { resolveNames(cNames = cNamesDummy, auxData = Data(compounds = this)) }
        CheckArg.throws<IllegalArgumentException, _>(c4,
            "compound spam requires a variant"
        ).invokeSuspend { resolveNames(cNames = cNamesDummy, auxData = Data(compounds = this)) }
    }

    // TODO: coverage: invalid auxData.compounds.+variants ro
    @Test
    fun putEntries() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val auxData = Data(
            compounds = mapOf(
                CompoundName(CompoundBase("hello"), "world") to
                        CompoundInfo(1.toDuration(DurationUnit.DAYS)),
                CompoundName(CompoundBase("goodbye")) to
                        CompoundInfo(2.toDuration(DurationUnit.DAYS)),
            ),
            blends = mapOf(
                BlendName("waldo") to
                        BlendValue(mapOf(
                            CompoundName(CompoundBase("hello"), "world") to 6.9,
                            CompoundName(CompoundBase("testosterone"), "cypionate") to 4.20,
                        ))
            ),
            frequencies = mapOf(
                FrequencyName("soon") to
                        FrequencyValue(listOf(1_000_000.toDuration(DurationUnit.DAYS)))
            )
        )
        assertTrue(putEntries(auxData))

        val cNames = setOf(
            CompoundName(CompoundBase("testosterone"), "enanthate"),
            CompoundName(CompoundBase("goodbye")),
        )
        val cNamesExpected = setOf(
            CompoundName(CompoundBase("testosterone"), "enanthate"),
            // blend expansion
            CompoundName(CompoundBase("testosterone"), "propionate"),
            CompoundName(CompoundBase("testosterone"), "phenylpropionate"),
            CompoundName(CompoundBase("testosterone"), "isocaproate"),
            CompoundName(CompoundBase("testosterone"), "decanoate"),
            // auxData compound
            CompoundName(CompoundBase("goodbye")),
            // auxData blend expansion
            CompoundName(CompoundBase("hello"), "world"),
            CompoundName(CompoundBase("testosterone"), "cypionate") ,
        )
        val bNames = setOf(
            BlendName("sustanon 250"),
            // auxData
            BlendName("waldo"),
        )
        val fNames = setOf(
            FrequencyName("every day"),
            // auxData
            FrequencyName("soon")
        )
        val data = resolveNames(cNames, bNames, fNames)
        assertAll(
            { assertEquals(cNamesExpected, data.compounds.keys) },
            { assertEquals(bNames, data.blends.keys) },
            { assertEquals(fNames, data.frequencies.keys) }
        )
    }

    @Test
    fun `putEntries f-ro`() = runTestWithTempController(RO_ALL) {
        assertThrows<UnsupportedOperationException> {
            putEntries(Data())
        }
    }

    @Test
    fun upsertCompounds() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val u0Name = CompoundName(CompoundBase("testosterone"), "enanthate")
        val u0Time = 999.toDuration(DurationUnit.DAYS)
        val u0Data = CompoundInfo(u0Time)
        val u1Name = CompoundName(CompoundBase("testosterone"), "propionate")
        val u1Time = 99.toDuration(DurationUnit.DAYS)
        val u1Data = CompoundInfo(u1Time)

        assertTrue(putEntries(Data(compounds = mapOf(u0Name to u0Data, u1Name to u1Data))))
        val compounds = resolveNames(cNames = setOf(u0Name, u1Name)).compounds

        assertAll(
            { assertEquals(u0Time, compounds[u0Name]?.halfLife) },
            { assertEquals(u1Time, compounds[u1Name]?.halfLife) }
        )
    }

    @Test
    fun updateCompounds() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val u0Name = CompoundName(CompoundBase("testosterone"), "enanthate")
        val u0Time = 999.toDuration(DurationUnit.DAYS)
        val u0Data = ProxyMap<CompoundInfo>("halfLife" to u0Time)
        val u1Name = CompoundName(CompoundBase("testosterone"), "propionate")
        val u1Time = 99.toDuration(DurationUnit.DAYS)
        val u1Data = ProxyMap<CompoundInfo>("halfLife" to u1Time)

        assertTrue(updateCompounds(mapOf(u0Name to u0Data, u1Name to u1Data)))
        val compounds = resolveNames(cNames = setOf(u0Name, u1Name)).compounds

        assertAll(
            { assertEquals(u0Time, compounds[u0Name]?.halfLife) },
            { assertEquals(u1Time, compounds[u1Name]?.halfLife) }
        )
    }

    @Test
    fun `updateCompounds missing`() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val u0Name = CompoundName(CompoundBase("testosterone"))
        val u0Time = 999.toDuration(DurationUnit.DAYS)
        val u0Data = ProxyMap<CompoundInfo>("halfLife" to u0Time)

        assertFalse(updateCompounds(mapOf(u0Name to u0Data)))
    }

    // updateCompounds is wrapped inside a mustWrite{} so no point to using RO_ALL to test its validation
    @Test
    fun `updateCompounds f-invalid`() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val u0Name = CompoundName(CompoundBase("testosterone"), "enanthate")
        val u0Time = (-1).toDuration(DurationUnit.DAYS)

        CheckArg.throws<IllegalArgumentException, Unit>(Unit,
            "nonpositive halfLife (-1d)"
        ).invokeSuspend {
            val u0Data = ProxyMap<CompoundInfo>("halfLife" to u0Time)
           updateCompounds(mapOf(u0Name to u0Data))
        }
    }

    @Test
    fun `updateCompounds f-ro`() = runTestWithTempController(RO_ALL) {
        assertThrows<UnsupportedOperationException> {
            updateCompounds(emptyMap())
        }
    }

    @Test
    fun removeEntries() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val cNames = setOf(
            CompoundName(CompoundBase("testosterone"), "enanthate"),
            // in "sustanon 250" so this tests ordering of blend then compound del
            CompoundName(CompoundBase("testosterone"), "decanoate"),
        )
        val bNames = setOf(
            BlendName("sustanon 250"),
        )
        val fNames = setOf(
            FrequencyName("every day"),
            FrequencyName("every other day"),
        )
        assertTrue(removeEntries(cNames = cNames, bNames = bNames, fNames = fNames))
        assertFalse(getCompoundNames().isEmpty())
        // we only have one blend so a condition-less delete is indistinguishable from unique entry delete :(
        assertTrue(getBlendNames().isEmpty())
        assertFalse(getFrequencyNames().isEmpty())
    }

    @Test
    fun `removeEntries all-variant`() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val cNames = setOf(
            CompoundName.selectAllVariants("masteron")
        )
        assertTrue(removeEntries(cNames = cNames))
        assertDoesNotContain(getCompoundNames(), CompoundBase("masteron"))
    }

    @Test
    fun `removeEntries f-compound-missing`() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val cNames = setOf(
            CompoundName(CompoundBase("testosterone")),
        )
        assertFalse(removeEntries(cNames = cNames))
    }

    @Test
    fun `removeEntries f-compound-blend`() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val cNames = setOf(
            CompoundName.selectAllVariants("testosterone")
        )
        assertThrows<IllegalArgumentException> {
            removeEntries(cNames = cNames)
        }
    }

    @Test
    fun `removeEntries f-blend-missing`() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val bNames = setOf(
            BlendName("testosterone"),
        )
        assertFalse(removeEntries(bNames = bNames))
    }

    @Test
    fun `removeEntries f-frequency-missing`() = runTestWithTempController(RO(RoFlag.FORBID_CLEAR)) {
        val fNames = setOf(
            FrequencyName("never"),
        )
        assertFalse(removeEntries(fNames = fNames))
    }

    @Test
    fun `removeEntries f-ro`() = runTestWithTempController(RO_ALL) {
        assertThrows<UnsupportedOperationException> {
            removeEntries()
        }
    }

    @Test
    fun clearEntries() = runTestWithTempController(RO_NONE) {
        clearEntries()
        val names = getCompoundNames()
        assertTrue(names.isEmpty())
    }

    @Test
    fun `clearEntries f-ro`() = runTestWithTempController(RO_ALL) {
        assertThrows<UnsupportedOperationException> {
            assertFalse(clearEntries())
        }
    }

    @Test
    fun isEmpty() = runTestWithTempController(RO_NONE) {
        assertFalse(isEmpty())
        assertTrue(clearEntries())
        assertTrue(isEmpty())
    }

    companion object {

        private fun runTestWithTempController(
            ro: EnumSet<DataControllerTestSupport.RoFlag>,
            block: suspend DataController.() -> Unit
        ) =
            newTestController(ro).run {
                runTest {
                    block()
                }
            }
    }
}