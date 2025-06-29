package com.moshy.drugcalc.db.jooq

import com.moshy.drugcalc.db.generated.tables.references.*
import com.moshy.drugcalc.types.dataentry.*
import kotlin.math.*
import kotlin.time.*

internal suspend fun AsyncJooq.getFrequencies(names: Collection<FrequencyName>) =
    dsl {
        select(FREQUENCIES_ITEMS.FREQ_NAME, FREQUENCIES_ITEMS.INTERVAL_SECS)
            .from(FREQUENCIES)
            .join(FREQUENCIES_ITEMS)
            .on(FREQUENCIES_ITEMS.FREQ_NAME.eq(FREQUENCIES.NAME))
            .where(FREQUENCIES.NAME.`in`(names))
            .orderBy(FREQUENCIES.NAME.asc(), FREQUENCIES_ITEMS.ITEM_INDEX.asc())
    }.run {
        buildMap {
            var currentName: FrequencyName? = null
            var inner: MutableList<Duration>? = null
            /* If we're being paranoid about contiguity, we could check that
             * FREQUENCIES_ITEMS.ITEM_INDEX == len(inner) before every add.
             * This would ensure monotonicity.
             */
            suspendedBlockingFetch {
                forEach { (freqName, item) ->
                    requireNotNull(freqName) { "frequency name is null" }
                    requireNotNull(item) { "frequency item is null" }

                    if (currentName == null || currentName?.value != freqName) {
                        // move inner on blendName transition, not first row
                        if (currentName != null)
                            this@buildMap[currentName!!] = FrequencyValue(inner!! as List<Duration>)
                        inner = mutableListOf()
                        currentName = FrequencyName(freqName)
                    }
                    inner!!.add(item.toDuration())
                }
            }
            if (currentName != null && inner != null)
                this@buildMap[currentName] = FrequencyValue(inner)
        }
    }

/* no point in a singular putFrequency because we'll need a transaction to propagate changes to components anyway
 * (unless the DB is fully migrated to Postgres and array column types can be used)
 */
internal fun AsyncJooq.putFrequenciesQueries(frequencies: Map<FrequencyName, FrequencyValue>): List<QueryFactory> {
    if (frequencies.isEmpty())
        return emptyList()
    val names = mutableListOf<FrequencyName>()
    val itemsInSeconds = mutableListOf<List<Int>>()
    for ((name, items) in frequencies.entries) {
        checkFrequencyItemsPreconditions(name, items)
        name
            .let(names::add)
        items
            .map(Duration::tryConvertToSeconds)
            .let(itemsInSeconds::add)
    }
    return buildList {
        add {
            // delete from items so that the parent table can keep track of upserts
            delete(FREQUENCIES_ITEMS)
                .where(FREQUENCIES_ITEMS.FREQ_NAME.`in`(names))
                .asAnyQuery()
        }
        frequencies.keys.forEach {
            add {
                upsertQuery(FREQUENCIES, FREQUENCIES.NAME, it.value, FREQUENCIES.UPDATED)
                    .asTaggedAnyQuery()
            }
        }
        add {
            insertInto(
                FREQUENCIES_ITEMS,
                FREQUENCIES_ITEMS.FREQ_NAME, FREQUENCIES_ITEMS.INTERVAL_SECS, FREQUENCIES_ITEMS.ITEM_INDEX
            )
                .values(null as String?, null, null)
                .let(::batch)
                .apply {
                    for (i in 0..<names.size)
                        for ((index, duration) in itemsInSeconds[i].withIndex())
                            bind(names[i], duration, index)
                }.asAnyQuery()
        }
    }
}

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

private fun checkFrequencyItemsPreconditions(name: FrequencyName, items: List<Duration>) {
    require(items.isNotEmpty()) {
        "frequency $name must have 1+ components"
    }
}