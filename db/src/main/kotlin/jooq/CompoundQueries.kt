package com.moshy.drugcalc.db.jooq

import com.moshy.drugcalc.db.generated.tables.Compounds.Companion.COMPOUNDS
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.ProxyMap
import com.moshy.drugcalc.types.*
import kotlin.math.*
import kotlin.time.*
import org.jooq.*
import org.jooq.impl.DSL.row

internal suspend fun AsyncJooq.getCompounds(names: Collection<CompoundName>): Map<CompoundName, CompoundInfo> =
    dsl {
        select(
            COMPOUNDS.NAME, COMPOUNDS.VARIANT,
            COMPOUNDS.HALF_LIFE_SECS, COMPOUNDS.PCT_ACTIVE,
            COMPOUNDS.NOTE
        )
            .from(COMPOUNDS)
            .where(
                row(COMPOUNDS.NAME, COMPOUNDS.VARIANT)
                    .`in`(names.map { row(it.compound.value, it.variant) })
            )
    }.run {
        buildMap {
            suspendedBlockingFetch {
                forEachLazy { (name, variant, halfLifeSecs, pctActive, note) ->
                    requireNotNull(name) { "null compound" }
                    requireNotNull(variant) { "null variant" }
                    requireNotNull(halfLifeSecs) { "null halfLife" }
                    requireNotNull(pctActive) { "null pctActive" }
                    requireNotNull(note) { "null note" }
                    val cName = CompoundName(CompoundBase(name), variant)
                    val cInfo = CompoundInfo(halfLifeSecs.toDuration(), pctActive, note)
                    put(cName, cInfo)
                }
            }
        }
    }

internal fun DSLContext.putCompoundQuery(name: CompoundName, info: CompoundInfo): Query =
    upsertQuery(
        COMPOUNDS,
        listOf(COMPOUNDS.NAME, COMPOUNDS.VARIANT),
        listOf(name.compound.value, name.variant),
        listOf(COMPOUNDS.HALF_LIFE_SECS, COMPOUNDS.PCT_ACTIVE, COMPOUNDS.NOTE),
        listOf(info.halfLife.tryConvertToSeconds(), info.pctActive, info.note),
        COMPOUNDS.UPDATED
    )

internal fun AsyncJooq.putCompoundsQueries(compounds: Map<CompoundName, CompoundInfo>): List<QueryFactory>  =
    compounds.map { (k, v) ->
        { putCompoundQuery(k, v).asTaggedAnyQuery() }
    }

internal fun DSLContext.updateCompoundQuery(name: CompoundName, updateMap: ProxyMap<CompoundInfo>): Query =
    update(COMPOUNDS)
        .set(
            updateMap.keys.map { checkNotNull(cInfoToColumnName[it]) }
            .let { row(it + COMPOUNDS.UPDATED) },
            updateMap.entries.map { (k, v) -> checkNotNull(cInfoToColumnValue[k]?.invoke(checkNotNull(v))) }
                .let { row(it + null) }
        ).where(
            row(COMPOUNDS.NAME, COMPOUNDS.VARIANT)
                .`in`(row(name.compound.value, name.variant))
        )

internal fun DSLContext.deleteCompoundsQuery(names: Collection<CompoundName>): List<Query> =
    buildList {
        val allVariantSelectors = mutableListOf<String>()
        val nonAllVariantSelectors = mutableListOf<CompoundName>()
        for (c in names) {
            when (c.selectAllVariants) {
                true ->
                    allVariantSelectors.add(c.compound.value)
                false ->
                    nonAllVariantSelectors.add(c)
            }
        }
        if (allVariantSelectors.isNotEmpty())
            add(deleteQuery(allVariantSelectors, COMPOUNDS, COMPOUNDS.NAME))
        if (nonAllVariantSelectors.isNotEmpty()) {
            val condition =
                row(COMPOUNDS.NAME, COMPOUNDS.VARIANT)
                    .`in`(nonAllVariantSelectors.map { row(it.compound.value, it.variant) })
            add(deleteQuery(COMPOUNDS, condition))
        }
    }

private val cInfoToColumnName = mapOf(
    "halfLife" to COMPOUNDS.HALF_LIFE_SECS,
    "pctActive" to COMPOUNDS.PCT_ACTIVE,
    "note" to COMPOUNDS.NOTE
)

@Suppress("UNCHECKED_CAST")
private val cInfoToColumnValue = mapOf(
    "halfLife" to { dur: Duration -> dur.tryConvertToSeconds() } as (Any) -> Any,
    "pctActive" to { it: Double -> it } as (Any) -> Any,
    "note" to { it: String -> it } as (Any) -> Any
)


/* The Duration <-> Int converters are implementation details of this table and should not be unified with
 * equivalents for Frequencies.
 */

private fun Duration.tryConvertToSeconds() =
    toDouble(DurationUnit.SECONDS).run {
        require(floor(this) == ceil(this)) {
            "${this@tryConvertToSeconds} is not a multiple of seconds"
        }
        toInt()
    }

private fun Int.toDuration() =
    toDuration(DurationUnit.SECONDS)
