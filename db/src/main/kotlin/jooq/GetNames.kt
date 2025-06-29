package com.moshy.drugcalc.db.jooq

import com.moshy.containers.ListAsSortedSet
import com.moshy.containers.assertIsSortedSet
import org.jooq.*
import org.jooq.impl.TableImpl

/** Reflective name fetcher given [table], [condition], and element [T].
 *  Provides stronger null safety than Jooq's built-in approach.
 */
@JvmName("GetNamesVararg")
internal suspend inline fun <reified T : Comparable<T>, R : Record> AsyncJooq.getNames(
    table: TableImpl<R>,
    condition: Condition? = null,
    vararg nameColumns: TableField<R, String?>,
    noinline ctor: (List<String>) -> T,
): ListAsSortedSet<T> =
    getNames(table, condition, nameColumns, ctor)

internal suspend fun <T : Comparable<T>, R : Record> AsyncJooq.getNames(
    table: TableImpl<R>,
    condition: Condition? = null,
    nameColumns: Array<out TableField<R, String?>>,
    ctor: (List<String>) -> T,
): ListAsSortedSet<T> {
    val query: ResultQuery<Record> = dsl {
        selectDistinct(*nameColumns)
            .from(table)
            .run { condition?.let(::where) ?: this }
            .orderBy(*nameColumns)
    }
    val nParams = nameColumns.size
    return query.suspendedBlockingFetch {
        buildList {
            forEachLazy { row: Record ->
                /* this will throw IAE from either null field or wrong field count, but we don't expect either,
                 * so don't bother catching
                 */
                val args = (0..<nParams).map { requireNotNull(row.get(it)) as String }
                add(ctor(args))
            }
        }.assertIsSortedSet()
    }
}
