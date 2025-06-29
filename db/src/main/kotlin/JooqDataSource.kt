
package com.moshy.drugcalc.db
import com.moshy.drugcalc.common.PreferredIODispatcher
import com.moshy.drugcalc.db.generated.tables.references.*
import com.moshy.drugcalc.db.jooq.*
import com.moshy.drugcalc.types.datasource.DataSourceDelegate
import com.moshy.drugcalc.types.dataentry.*

import com.moshy.ProxyMap
import com.moshy.containers.*
import com.zaxxer.hikari.*
import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import org.jooq.*
import org.jooq.impl.*

/** Delegate for abstracting away database connection implementation from database operations.
 *
 * An implementation may have something like
 * ```
 * class FooDelegate(private val db: FooDatabase): DataSourceDelegate {
 *     [...]
 *     override suspend fun OP(...): ... =
 *         db.OP(...)
 *  }
 *  ```
 */
internal class JooqDataSource(
    dataSource: HikariDataSource
) : DataSourceDelegate {
    private val aJooq =
        PreferredIODispatcher().let { ioDisp ->
            AsyncJooq(
                DSL.using(
                    DefaultConfiguration().apply {
                        set(DataSourceConnectionProvider(dataSource))
                        set(inferDialectFromJdbcUrl(dataSource))
                        set(ExecutorProvider(ioDisp::asExecutor))
                    }
                ),
                ioDisp + CoroutineName("Jooq-SuspendIO")
            )
        }

    override suspend fun getCompoundNames(): ListAsSortedSet<CompoundBase> =
        aJooq.op {
            this.getNames(COMPOUNDS, null, COMPOUNDS.NAME)
                { (c) -> CompoundBase(c) }
        }

    override suspend fun getVariantsForCompound(forName: CompoundBase): ListAsSortedSet<String>? =
        aJooq.op {
            val names = this.getNames(
                COMPOUNDS, COMPOUNDS.NAME.eq(forName.value),
                COMPOUNDS.VARIANT
            ) { (v) -> v }
            when {
                names.isEmpty() -> null
                names == listOf("") -> emptyList<String>().assertIsSortedSet()
                else -> names
            }
        }

    override suspend fun getCompounds(names: Collection<CompoundName>): Map<CompoundName, CompoundInfo> =
        aJooq.op { this.getCompounds(names) }

    override suspend fun updateCompounds(compounds: Map<CompoundName, ProxyMap<CompoundInfo>>): Boolean =
        aJooq.op {
            val queryFactories: List<DSLContext.() -> AsyncJooq.AnyQuery> =
                compounds.entries.map { (k, v) ->
                    { updateCompoundQuery(k, v).asTaggedAnyQuery() }
                }
            suspendedBlockingTransaction(
                *queryFactories.toTypedArray()
            ).anyChanged()
        }

    override suspend fun getBlendNames(): ListAsSortedSet<BlendName> =
        aJooq.op {
            this.getNames(BLENDS, null, BLENDS.NAME)
                { (b) -> BlendName(b) }
        }

    override suspend fun getBlends(names: Collection<BlendName>): Map<BlendName, BlendValue> =
        aJooq.op { this.getBlends(names) }

    override suspend fun getFrequencyNames(): ListAsSortedSet<FrequencyName> =
        aJooq.op {
            this.getNames(FREQUENCIES, null, FREQUENCIES.NAME)
                { (f) -> FrequencyName(f) }
        }

    override suspend fun getFrequencies(names: Collection<FrequencyName>): Map<FrequencyName, FrequencyValue> =
        aJooq.op { this.getFrequencies(names) }

    override suspend fun putBulk(
        compounds: Map<CompoundName, CompoundInfo>,
        blends: Map<BlendName, BlendValue>,
        frequencies: Map<FrequencyName, FrequencyValue>
    ): Boolean =
        aJooq.op {
            val compoundsStmts = putCompoundsQueries(compounds)
            val blendsStmts = putBlendsQueries(blends)
            val frequenciesStmts = putFrequenciesQueries(frequencies)
            return@op suspendedBlockingTransaction(
                compoundsStmts + blendsStmts + frequenciesStmts
            ).anyChanged()
        }

    override suspend fun deleteBulk(
        compounds: Collection<CompoundName>,
        blends: Collection<BlendName>,
        frequencies: Collection<FrequencyName>
    ): Boolean = aJooq.op {
        buildList {
            when (getDeletionKind(blends, DataSourceDelegate.DELETE_ALL_BLENDS)) {
                DeleteKind.NONE -> {}
                DeleteKind.ALL -> add(
                    deleteQuery(null, BLENDS, null)
                )
                DeleteKind.FILTER -> add(
                    deleteQuery(blends.map(BlendName::value), BLENDS, BLENDS.NAME)
                )
            }
            when (getDeletionKind(compounds, DataSourceDelegate.DELETE_ALL_COMPOUNDS)) {
                DeleteKind.NONE -> {}
                DeleteKind.ALL -> add(
                    deleteQuery(null, COMPOUNDS, null)
                )
                DeleteKind.FILTER -> add {
                   deleteCompoundsQuery(compounds).asTaggedAnyQuery()
                }
            }
            when (getDeletionKind(frequencies, DataSourceDelegate.DELETE_ALL_FREQUENCIES)) {
                DeleteKind.NONE -> {}
                DeleteKind.ALL -> add(
                    deleteQuery(null, FREQUENCIES, null)
                )
                DeleteKind.FILTER -> add(
                    deleteQuery(frequencies.map(FrequencyName::value), FREQUENCIES, FREQUENCIES.NAME)
                )
            }
        }.let { suspendedBlockingTransaction(it) }.anyChanged()
    }

    override suspend fun isDbEmpty(): Boolean =
        aJooq.op { isDbEmpty() }

    override suspend fun getCompoundNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<CompoundName> =
        aJooq.op {
            getNamesCreatedOrUpdatedAfterTimestamp(
                timestamp,
                COMPOUNDS, COMPOUNDS.CREATED, COMPOUNDS.UPDATED, COMPOUNDS.NAME, COMPOUNDS.VARIANT
            )
                { (c, v) -> CompoundName(CompoundBase(c), v) }
        }

    override suspend fun getBlendNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<BlendName> =
        aJooq.op {
            getNamesCreatedOrUpdatedAfterTimestamp(
                timestamp,
                BLENDS, BLENDS.CREATED, BLENDS.UPDATED, BLENDS.NAME
            )
                { (b) -> BlendName(b) }
        }

    override suspend fun getFrequencyNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<FrequencyName> =
        aJooq.op {
            getNamesCreatedOrUpdatedAfterTimestamp(
                timestamp,
                FREQUENCIES, FREQUENCIES.CREATED, FREQUENCIES.UPDATED, FREQUENCIES.NAME
            )
                { (f) -> FrequencyName(f) }
        }
}

private fun inferDialectFromJdbcUrl(hikariConfig: HikariConfig) =
    hikariConfig.jdbcUrl.run {
        when {
            /* Other DB engines are supported by Jooq but may require changing the DDL in the migration scripts, which
             * is undesirable.
             * We will only concern ourselves with the immediate one and a possible successor should scaling to
             * concurrent writes (i.e. multiple users) become necessary.
             * It is not a design priority to support R2DBC.
             */
            startsWith("jdbc:sqlite:") -> SQLDialect.SQLITE
            startsWith("jdbc:postgres") -> SQLDialect.POSTGRES
            else -> throw IllegalArgumentException("unexpected jdbc url $this")
        }
    }

private enum class DeleteKind {
    NONE, ALL, FILTER,
}
private fun <T> getDeletionKind(names: Collection<T>, singletonForDeleteAll: Collection<T>): DeleteKind =
    when {
        names === singletonForDeleteAll -> DeleteKind.ALL
        names.isNotEmpty() -> DeleteKind.FILTER
        else -> DeleteKind.NONE
    }
