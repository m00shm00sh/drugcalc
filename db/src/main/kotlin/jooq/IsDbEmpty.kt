package com.moshy.drugcalc.db.jooq

import com.moshy.drugcalc.db.generated.tables.references.*
import org.jooq.impl.DSL.*

internal suspend fun AsyncJooq.isDbEmpty(): Boolean =
    dsl {
        select(
            field(
                select(one())
                    .where(
                        exists(
                            select(COMPOUNDS.NAME)
                                .from(COMPOUNDS)
                        )
                    )
            ).add(
                field(
                    select(one())
                        .where(
                            exists(
                                select(BLENDS.NAME)
                                    .from(BLENDS)
                            )
                        )
                )
            ).add(
                field(
                    select(one())
                        .where(
                            exists(
                                select(FREQUENCIES.NAME)
                                    .from(FREQUENCIES)
                            )
                        )
                )
            )
        )
    }.suspendedBlockingFetch { (fetchSingle().value1() ?: 0) == 0 }
