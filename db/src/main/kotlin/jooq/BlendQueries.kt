package com.moshy.drugcalc.db.jooq

import com.moshy.drugcalc.db.generated.tables.references.*
import com.moshy.drugcalc.types.dataentry.*

internal suspend fun AsyncJooq.getBlends(
    names: Collection<BlendName>
): Map<BlendName, BlendValue> =
    dsl {
        select(
            BLENDS.NAME,
            BLEND_COMPONENTS.COMPONENT_COMPOUND, BLEND_COMPONENTS.COMPONENT_VARIANT,
            BLEND_COMPONENTS.COMPONENT_DOSE,
            BLENDS.NOTE,
        )
            .from(BLENDS)
            .join(BLEND_COMPONENTS)
            .on(BLEND_COMPONENTS.BLEND_NAME.eq(BLENDS.NAME))
            .where(BLENDS.NAME.`in`(names))
            .orderBy(BLENDS.NAME.asc())
    }.run {
        buildMap {
            var currentName: BlendName? = null
            var noteForName: String? = null
            var inner: MutableMap<CompoundName, Double>? = null
            suspendedBlockingFetch {
                forEachLazy { (blendName, componentCompound, componentVariant, componentDose, note) ->
                    requireNotNull(blendName) { "null blend name" }
                    requireNotNull(componentCompound) { "null component compound" }
                    requireNotNull(componentVariant) { "null component variant" }
                    requireNotNull(componentDose) { "null component dose" }
                    requireNotNull(note) { "null note" }

                    val blendNameAsCompoundName = blendName.let(::BlendName)
                    if (currentName == null || currentName != blendNameAsCompoundName) {
                        // move inner on blendName transition, not first row
                        if (currentName != null)
                            this@buildMap[currentName!!] =
                                BlendValue(inner!! as Map<CompoundName, Double>, note)
                        inner = mutableMapOf()
                        currentName = blendNameAsCompoundName
                        noteForName = note
                    }
                    inner!![CompoundName(CompoundBase(componentCompound), componentVariant)] = componentDose
                }
            }
            if (currentName != null && inner != null)
                this@buildMap[currentName] = BlendValue(inner as Map<CompoundName, Double>, noteForName!!)
        }
    }

// no point in a singular putBlend because we'll need a transaction to propagate changes to components anyway
internal fun AsyncJooq.putBlendsQueries(blends: Map<BlendName, BlendValue>): List<QueryFactory> {
    if (blends.isEmpty())
        return emptyList()
    return buildList {
        add {
            // delete from components so that the parent table can keep track of upserts
            delete(BLEND_COMPONENTS)
                .where(BLEND_COMPONENTS.BLEND_NAME.`in`(blends.keys))
                .asAnyQuery()
        }
        blends.forEach { (k, v) ->
            add {
                upsertQuery(BLENDS, listOf(BLENDS.NAME), listOf(k.value),
                    listOf(BLENDS.NOTE), listOf(v.note), BLENDS.UPDATED)
                    .asTaggedAnyQuery()
            }
        }
        add {
            insertInto(
                BLEND_COMPONENTS,
                BLEND_COMPONENTS.BLEND_NAME,
                BLEND_COMPONENTS.COMPONENT_COMPOUND, BLEND_COMPONENTS.COMPONENT_VARIANT,
                BLEND_COMPONENTS.COMPONENT_DOSE
            )
                .values(null as String?, null as String?, null as String?, null as Double?)
                .let(::batch)
                .apply {
                    for ((blend, components) in blends)
                        for ((cName, cDose) in components)
                            bind(blend, cName.compound, cName.variant, cDose)
                }.asAnyQuery()
        }
    }
}
