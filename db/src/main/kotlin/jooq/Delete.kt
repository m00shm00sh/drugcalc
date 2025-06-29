package com.moshy.drugcalc.db.jooq

import org.jooq.*

internal fun <R : Record> DSLContext.deleteQuery(
    table: Table<R>,
    condition: Condition?
): Query =
    deleteFrom(table)
        .run {
            condition?.let {
                where(condition)
            } ?: this
        }

internal fun <R : Record> DSLContext.deleteQuery(
    names: Collection<String>?,
    table: Table<R>,
    column: TableField<R, String?>?
): Query {
    val condition = names?.let { requireNotNull(column).`in`(names) }
    return deleteQuery(table, condition)
}

internal fun <R : Record> AsyncJooq.deleteQuery(
    names: Collection<String>?,
    table: Table<R>,
    column: TableField<R, String?>?,
    tagged: Boolean = true
): QueryFactory =
    {
        deleteQuery(names, table, column).run {
                when (tagged) {
                    true -> asTaggedAnyQuery()
                    false -> asAnyQuery()
                }
            }
    }
