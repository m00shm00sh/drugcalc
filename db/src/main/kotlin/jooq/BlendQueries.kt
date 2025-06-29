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
            BLEND_COMPONENTS.COMPONENT_DOSE
        )
            .from(BLENDS)
            .join(BLEND_COMPONENTS)
            .on(BLEND_COMPONENTS.BLEND_NAME.eq(BLENDS.NAME))
            .where(BLENDS.NAME.`in`(names))
            .orderBy(BLENDS.NAME.asc())
    }.run {
        buildMap {
            var currentName: BlendName? = null
            var inner: MutableMap<CompoundName, Double>? = null
            suspendedBlockingFetch {
                forEachLazy { (blendName, componentCompound, componentVariant, componentDose) ->
                    requireNotNull(blendName) { "null blend name" }
                    requireNotNull(componentCompound) { "null component compound" }
                    requireNotNull(componentVariant) { "null component variant" }
                    requireNotNull(componentDose) { "null component dose" }

                    val blendNameAsCompoundName = blendName.let(::BlendName)
                    if (currentName == null || currentName != blendNameAsCompoundName) {
                        // move inner on blendName transition, not first row
                        if (currentName != null)
                            this@buildMap[currentName!!] = BlendValue(inner!! as Map<CompoundName, Double>)
                        inner = mutableMapOf()
                        currentName = blendNameAsCompoundName
                    }
                    inner!![CompoundName(CompoundBase(componentCompound), componentVariant)] = componentDose
                }
            }
            if (currentName != null && inner != null)
                this@buildMap[currentName] = BlendValue(inner as Map<CompoundName, Double>)
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
        blends.keys.forEach {
            add {
                upsertQuery(BLENDS, BLENDS.NAME, it.value, BLENDS.UPDATED)
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
