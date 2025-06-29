package com.moshy.drugcalc.db.jooq

import org.jooq.*
import org.jooq.impl.TableImpl
import java.time.LocalDateTime as JLocalDateTime

internal fun <R : Record> DSLContext.upsertQuery(
    table: TableImpl<R>,
    nameColumn: TableField<R, String?>,
    name: String,
    updatedColumn: TableField<R, JLocalDateTime?>,
): Query =
    upsertQuery(table, listOf(nameColumn), listOf(name),
        emptyList(), emptyList(), updatedColumn)

/** Upsert query on [table] with identity columns [nameColumns] having values [names]
 *  and data columns [dataColumns] having values [db].
 *
 *  Undesirable behavior will occur if `dataColumns[i]` is a `TableField<R, T!>` and `data[i]` is not a `T!`.
 */
internal fun <R : Record> DSLContext.upsertQuery(
    table: TableImpl<R>,
    nameColumns: List<TableField<R, String?>>,
    names: List<String>,
    dataColumns: List<TableField<R, *>>,
    data: List<Any>,
    updatedColumn: TableField<R, JLocalDateTime?>,
): Query {
    check(nameColumns.size == names.size)
    check(dataColumns.size == data.size)
    return insertInto(table, nameColumns + dataColumns)
        .values(names + data)
        .onConflict(nameColumns)
        .doUpdate()
        .set(updatedColumn, null as JLocalDateTime?)
        .apply {
            if (dataColumns.isNotEmpty() && data.isNotEmpty())
                dataColumns.zip(data) { a, b ->
                    @Suppress("UNCHECKED_CAST")
                    set(a as Field<Any>, b)
                }
        }
}