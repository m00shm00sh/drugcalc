package com.moshy.drugcalc.db.jooq

import com.moshy.containers.ListAsSortedSet
import kotlinx.datetime.*
import java.time.LocalDateTime as JLocalDateTime
import org.jooq.*
import org.jooq.impl.TableImpl

internal suspend inline fun <reified T : Comparable<T>, R : Record> AsyncJooq.getNamesCreatedOrUpdatedAfterTimestamp(
    ts: Instant,
    table: TableImpl<R>,
    createdColumn: TableField<R, JLocalDateTime?>,
    updatedColumn: TableField<R, JLocalDateTime?>,
    vararg nameColumns: TableField<R, String?>,
    noinline ctor: (List<String>) -> T
): ListAsSortedSet<T> {
    val jTS = ts.toJLocalDateTime()
    val condition =
        createdColumn.ge(jTS)
            .or(updatedColumn.ge(jTS))
    return getNames(table, condition, nameColumns, ctor)
}

// jooq generates java.time.LocalDateTime for TIMESTAMP fields; for SQLite, it's known that's UTC
private fun Instant.toJLocalDateTime() = toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime()
