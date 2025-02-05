package com.moshy.drugcalc.repo

import com.github.benmanes.caffeine.cache.Caffeine
import com.moshy.drugcalc.calc.Config
import com.moshy.drugcalc.calcdata.DataStore
import com.moshy.drugcalc.db.*
import com.moshy.drugcalc.io.*
import com.moshy.drugcalc.misc.applyDiff
import com.moshy.drugcalc.misc.computeCompositeDiff
import com.moshy.drugcalc.misc.reversed
import com.moshy.drugcalc.misc.summarized
import com.moshy.drugcalc.util.AppConfig
import com.moshy.drugcalc.util.ContextNameLogger
import com.moshy.drugcalc.util.check
import com.sksamuel.aedile.core.Cache
import com.sksamuel.aedile.core.asCache
import com.sksamuel.aedile.core.asLoadingCache
import com.sksamuel.aedile.core.expireAfterAccess
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger

internal class Repository(private val repoConfig: AppConfig.Repo,
                          private val db: Database)
{
    private val repoLogger = LoggerFactory.getLogger("repo")
    /*
     * Stubs for pub-sub functionality. No plans to implement for now as the cache doesn't use a
     * freshness bit mechanism due to it querying the DB for revision number.
     */
    private fun onReceiveRemoteUpdateNotification() {}
    private fun announceUpdateNotification() {}

    private fun <K, V> newCache(policy: AppConfig.Repo.CacheEvictionPolicy) =
        Caffeine
            .newBuilder()
            .apply { policy.size?.let { maximumSize(it) } }
            .apply { policy.accessTime?.let { expireAfterAccess(it) } }
            .asCache<K, V>()
    private fun <K, V> newLoadingCache(compute: suspend (K) -> V) =
        Caffeine
            .newBuilder()
            .asLoadingCache(compute)

    private val dataCache = newCache<Int, CachedData>(repoConfig.objectCachePolicy)
    private val configCache = newCache<Int, CachedConfig>(repoConfig.configCachePolicy)
    private val loggerCache = newLoadingCache<String, ContextNameLogger> { ContextNameLogger(repoLogger, it) }

    // this variable shouldn't be used outside (get|update)LatestObject since they include revision number in their
    // return values
    private val latestDataRevision = AtomicInteger(-1)
    private val latestConfigRevision = AtomicInteger(-1)

    /** Get latest object. Returns revision alongside with complete data. */
    suspend fun getLatestData(): Pair<Int, CachedData> =
        getLatestObject(
            loggerCache.get("getLatestObject[data]"), db,
            latestDataRevision, dataCache, ObjectType.DATA,
            CachedData::invoke, serializer<FullData>()
        )
    /** Get latest config. Returns revision alongside with complete data. */
    suspend fun getLatestConfig(): Pair<Int, CachedConfig> =
        getLatestObject(
            loggerCache.get("getLatestObject[config"), db,
            latestConfigRevision, configCache, ObjectType.CONFIG,
            CachedConfig::invoke, serializer<ConfigMap>()
        )

    /**
     * Update the latest data object, if the [diff] doesn't produce an invalid object.
     *
     * Failure can happen due to the following cases:
     * 1. concurrent database writes make the cached revision version out of date
     * 2. the diff produces an invalid object when applied against the current latest object
     */
    private suspend fun updateLatestDataObject(fDiff: FullDiffData, revertTo: Int? = null): Int {
        require (fDiff.compounds.isNotEmpty() || fDiff.blends.isNotEmpty() || fDiff.frequencies.isNotEmpty()) {
            "empty diff"
        }

        requireEditable()
        return updateLatestObject(
            loggerCache.get("updateLatestObject[data]"), db, DataTable, latestDataRevision, dataCache,
            getLatest = ::getLatestData,
            createNewObject = { it.applyDiff(fDiff) },
            toCacheableData = { toFullData() },
            prepareInsertStmt = {
                if (fDiff.compounds.isNotEmpty())
                    this[DataObjects.diffCompounds] = fDiff.compounds
                if (fDiff.blends.isNotEmpty())
                    this[DataObjects.diffBlends] = fDiff.blends
                if (fDiff.frequencies.isNotEmpty())
                    this[DataObjects.diffFrequencies] = fDiff.frequencies
            },
            dataSerializer = serializer<FullData>(),
            cachedItemCtor = ::CachedData,
            newLatest = revertTo
        )
    }
    suspend fun updateLatestDataObject(diff: DiffData): Int {
        val ds = getLatestData().second.state
        val fdd = diff.toFullDiffData(ds)
        return updateLatestDataObject(fdd)
    }

    /**
     * Update the latest config object, if the [lens] doesn't produce an invalid config.

     * Failure can happen due to the following cases:
     * 1. concurrent database writes make the cached revision version out of date
     * 2. the diff produces an invalid object when applied against the current latest object
     */
    suspend fun updateLatestConfigObject(lens: ConfigMap): Int {
        requireEditable()
        lateinit var newObjectAsLens: ConfigMap
        return updateLatestObject(
            loggerCache.get("updateLatestObject[config]"), db, ConfigTable, latestConfigRevision, configCache,
            getLatest = ::getLatestConfig,
            createNewObject = { it.applyLens(lens) },
            toCacheableData = {
                newObjectAsLens = ConfigMap(this)
                return@updateLatestObject newObjectAsLens
            },
            prepareInsertStmt = {
                this[ConfigObjects.config] = newObjectAsLens
            },
            dataSerializer = serializer<ConfigMap>(),
            cachedItemCtor = ::CachedConfig
        )
    }

    /**
     * Check that applying diff against specified or current data produces a valid data.
     * If successful, it returns an integer that's the revision validated against.
     */
    suspend fun checkDataDiff(diff: DiffData) : Int =
        checkDiff(
            loggerCache.get("checkDiff[data]"),
            { getDataAtRevision(it) },
            { getLatestData() },
            { CachedData(it) },
            { applyDiff(it.toFullDiffData(this)) },
            diff
        )
    /**
     * Check that applying diff against specified or current config produces a valid config.
     * If successful, it returns an integer that's the revision validated against.
     */
    suspend fun checkConfigDiff(lens: ConfigMap) : Int =
        checkDiff(
            loggerCache.get("checkDiff[config]"),
            cacheGetOrFetch = { error("unexpected invocation") },
            { getLatestConfig() },
            { CachedConfig(it) },
            { applyLens(it.diff) },
            object : HasParentRevision {
                override val parentRevision = null
                val diff = lens
            }
        )

    /** Get a summary of data revisions. */
    suspend fun getDataRevisions(until: Int? = null, limit: Int? = null):
        List<RevisionSummary.Data> {
        require (until == null || until > 0) {
            "until out of range"
        }
        require(limit == null || limit > 0) {
            "limit out of range"
        }
        val defaultLimit = 20
        return suspendTransaction(db) {
            getRevisions(
                loggerCache.get("getRevisions[data]"), until, limit ?: defaultLimit,
                DataTable,
                listOf(
                    DataObjects.diffCompounds, DataObjects.diffBlends, DataObjects.diffFrequencies,
                    DataObjects.parent,
                )
            ) {
                val (delC, addC) = it[DataObjects.diffCompounds].summarized()
                val (delB, addB) = it[DataObjects.diffBlends].summarized()
                val (delF, addF) = it[DataObjects.diffFrequencies].summarized()
                RevisionSummary.Data(
                    it[DataObjects.revision], it[DataObjects.createTime],
                    delC + delB + delF,
                    addC + addB + addF,
                    it[DataObjects.parent],
                )
            }
        }
    }
    /** Get a summary of config revisions. */
    suspend fun getConfigRevisions(until: Int? = null, limit: Int? = null): List<RevisionSummary.Config> {
        require (until == null || until > 0) {
            "until out of range"
        }
        require(limit == null || limit > 0) {
            "limit out of range"
        }
        val defaultLimit = 5
        val rows  = suspendTransaction(db) {
            getRevisions(
                loggerCache.get("getRevisions[config]"), until, limit ?: defaultLimit,
                ConfigTable, listOf(ConfigObjects.config)
            ) {
                Triple(
                    it[ConfigObjects.revision], it[ConfigObjects.createTime],
                    it[ConfigObjects.config]
                )
            }
        }
        return rows
            .let {
                listOf(Triple(-1, Instant.DISTANT_PAST, ConfigMap())) + it
            }.zipWithNext { prev, cur ->
                RevisionSummary.Config(cur.first, cur.second, cur.third.size, (cur.third - prev.third).size)
            }
    }

    /** Get the diff pertaining to a revision. */
    suspend fun getDataDiffForRevision(revision: Int): FullDiffData {
        // this is uncached because it's the direct row object and not a complete object
        return suspendTransaction(db) {
            loggerCache.get("getDiffForRevision[data]").debug("fetch revision {}", revision)
            DataObjects
                .select(listOf(DataObjects.diffCompounds, DataObjects.diffBlends, DataObjects.diffFrequencies))
                .where(DataObjects.revision eq revision)
                .singleOrNull()
                ?.let {
                    FullDiffData(
                        compounds = it[DataObjects.diffCompounds],
                        blends = it[DataObjects.diffBlends],
                        frequencies = it[DataObjects.diffFrequencies]
                    )
                }
                ?: throw NoSuchElementException("revision $revision not found")
        }
    }
    /** Get the diff pertaining to a revision. */
    suspend fun getConfigDiffForRevision(revision: Int): ConfigMap {
        // this is uncached because it's the direct row object and not a complete object
        return suspendTransaction(db) {
            loggerCache.get("getDiffForRevision[config]").debug("fetch revision {}", revision)
            ConfigObjects
                .select(ConfigObjects.config)
                .where(ConfigObjects.revision.between(revision - 1, revision))
                .map { it[ConfigObjects.config] }
                .let {
                    when (it.size) {
                        2 -> it[1] - it[0]
                        1 -> it[0]
                        else -> throw NoSuchElementException("revision $revision and its predecessor not found")
                    }
                }
        }
    }

    /** Get the computed data state at a revision. */
    suspend fun getDataAtRevision(revision: Int): CachedData {
        val logger = loggerCache.get("getStateAtRevision[data]")
        /* Caffeine.get() { Caffeine.get() { } } causes a deadlock;
         * work around it by separating the fetches
         */
        val (thisRev, thisData) = getLatestData()
        if (revision == thisRev)
            return thisData
        if (revision > thisRev)
            throw NoSuchElementException("revision $revision not found")

        // we go backwards from Head to leverage the caching that headState provides
        return dataCache.get(revision) {
            logger.debug("compute state at revision {}", revision)
            val diff =
                suspendTransaction(db) {
                    DataObjects
                        .select(DataObjects.diffCompounds, DataObjects.diffBlends, DataObjects.diffFrequencies)
                        .where(DataObjects.revision.between(revision + 1, thisRev))
                        .map {
                            FullDiffData(
                                compounds = it[DataObjects.diffCompounds],
                                blends = it[DataObjects.diffBlends],
                                frequencies = it[DataObjects.diffFrequencies]
                            )
                        }
                }.computeCompositeDiff()
            CachedData(thisData.data.applyDiff(diff.reversed()))
        }
    }
    /** Get the config state at a revision.
     *
     * Semantically, this differs from getConfigDiffForRevision because it may include inactive variables.
     */
    suspend fun getConfigAtRevision(revision: Int): CachedConfig {
        val logger = loggerCache.get("getStateAtRevision[config]")
        return configCache.get(revision) {
            logger.debug("fetch {}", revision)
            // this is a trivial db fetch so we don't need to put it into its own function
            val rows = suspendTransaction(db) {
                ConfigObjects
                    .select(ConfigObjects.config)
                    .where(ConfigObjects.revision eq revision)
                    .limit(1)
                    .map {
                        it[ConfigObjects.config]
                    }
            }
            logger.debug("for {}, numRows={}", revision, rows.size)
            when (rows.size) {
                0 -> throw NoSuchElementException("revision $revision not found")
                1 -> CachedConfig(rows[0])
                else -> logger.check(false) { "this should be unreachable" } as Nothing
            }
        }
    }

    /** Undo the latest data add by reverting to its parent. */
    suspend fun undoLatestData() {
        requireEditable()
        val (latestRevision, _) = getLatestData()
        val parent = suspendTransaction(db) {
            DataObjects
                .select(DataObjects.parent)
                .where(DataObjects.revision eq latestRevision)
                .single()
                .let {
                    it[DataObjects.parent]
                }
        }
        val diffReversed = getDataDiffForRevision(latestRevision).reversed()
        updateLatestDataObject(diffReversed, revertTo = parent)
        dataCache.invalidate(latestRevision)
    }
    fun undoLatestConfig(): Nothing =
        throw UnsupportedOperationException("the config model cannot go back in time")

    private fun requireEditable() {
        if (repoConfig.readOnlyMode) {
            throw UnsupportedOperationException("read-only mode")
        }
    }
}

private suspend fun <Object, FullData, CachedItem: Cacheable<FullData, Object>>
getLatestObject(
    logger: ContextNameLogger,
    db: Database,
    revisionCounter: AtomicInteger,
    cache: Cache<Int, CachedItem>,
    objectType: ObjectType,
    objectCreator: (FullData) -> CachedItem,
    dataDeserializer: KSerializer<FullData>
): Pair<Int, CachedItem> {
    var didUpdateLastRevision = false
    val latestRevision = run {
        var latestRevision: Int = -1
        while (!didUpdateLastRevision) {
            val cachedRevision = revisionCounter.get()
            logger.trace("oType={} cachedRev={}", objectType, cachedRevision)
            latestRevision = when (cachedRevision) {
                -1 -> {
                    val dbLatestRevision = suspendTransaction(db) {
                        Caches
                            .select(Caches.revision)
                            .where(Caches.objType eq objectType)
                            .singleOrNull()
                            ?.let {
                                it[Caches.revision]
                            } ?: throw IllegalStateException("expected revision")
                    }
                    logger.trace("oType={} try-set cachedRev={}", objectType, latestRevision)
                    didUpdateLastRevision = revisionCounter.compareAndSet(cachedRevision, dbLatestRevision)
                    dbLatestRevision
                }
                else -> {
                    didUpdateLastRevision = true
                    cachedRevision
                }
            }
        }
        logger.check(latestRevision != -1) {
            "latest revision is -1"
        }
        return@run latestRevision
    }

    val data = cache.get(latestRevision) {
        // offload responsibility of head data computation to updater
        val data = suspendTransaction(db) {
            Caches
                .select(Caches.data)
                .where((Caches.objType eq objectType) and (Caches.revision eq latestRevision))
                .singleOrNull()
                ?.let {
                    Json.decodeFromString(dataDeserializer, it[Caches.data])
                } ?: throw IllegalStateException("expected data for revision $latestRevision")
        }
        objectCreator(data)
    }
    return latestRevision to data
}

private suspend fun <Object, DiffData: HasParentRevision, FullData, CachedItem: Cacheable<FullData, Object>>
checkDiff(
    logger: ContextNameLogger,
    cacheGetOrFetch: suspend (Int) -> CachedItem,
    latestGetter: suspend () -> Pair<Int, CachedItem>,
    objectCreator: (FullData) -> CachedItem,
    applyDiff: suspend Object.(DiffData) -> Object /* @Throws(IllegalArgumentException) */,
    diff: DiffData
) : Int {
    val (revision, state) = diff.parentRevision
        ?.let {
            it to cacheGetOrFetch(it).state
        } ?: latestGetter().let {
            (rev, data) -> rev to objectCreator(data.data).state
        }

    state.applyDiff(diff)
    return revision
}

private suspend fun <Object, FullData, CachedItem: Cacheable<FullData, Object>>
updateLatestObject(
    logger: ContextNameLogger,
    db: Database,
    table: _Table,
    revisionCounter: AtomicInteger,
    cache: Cache<Int, CachedItem>,
    getLatest: suspend () -> Pair<Int, CachedItem>,
    createNewObject: (Object) -> Object,
    toCacheableData: Object.() -> FullData,
    prepareInsertStmt: InsertStatement<Number>.() -> Unit,
    dataSerializer: KSerializer<FullData>,
    cachedItemCtor: (FullData, Object) -> CachedItem,
    newLatest: Int? = null,
): Int {
    val (revision, data) = getLatest()
    val dataObject = data.state
    val newRevision = newLatest ?: (revision + 1)
    val newObject = createNewObject(dataObject)
    val newData = newObject.toCacheableData()
    val objType = objectTypeForTable[table]
        ?: throw IllegalStateException("unhandled table ${table.tableView.tableName}")
    suspendTransaction(db) {
        logger.debug("try-insert revision $newRevision")
        if (
            table.tableView
                .insert {
                    it[table.columnView.revision] = newRevision
                    it[table.columnView.createTime] = Clock.System.now()
                    it.prepareInsertStmt()
                }
                .insertedCount != 1
        ) {
            require(false) { "couldn't insert revision $newRevision" }
        }
        if (
            Caches
                .update(
                    where = {
                        (Caches.revision eq revision) and
                        (Caches.objType eq objType)
                    }
                ) {
                    it[Caches.revision] = newRevision
                    it[Caches.data] = Json.encodeToString(dataSerializer, newData)
                } != 1
        ) {
            rollback()
            throw IllegalArgumentException("couldn't update cache to new revision; revision conflict")
        }
        commit()
    }
    revisionCounter.compareAndSet(revision, newRevision)
    cache.put(newRevision, cachedItemCtor(newData, newObject))
    return newRevision
}

@Suppress("UnusedReceiverParameter")
private fun <RowResult> Transaction.getRevisions(
    logger: ContextNameLogger,
    until: Int?,
    limit: Int,
    table: _Table,
    columns: List<Expression<*>>,
    auxWhere: Op<Boolean>? = null,
    evaluateRow: (ResultRow) -> RowResult,
): List<RowResult> {
    logger.debug("table {}: summarize revisions(until={}, lim={})", table.tableView.tableName, until, limit)
    return table.tableView
        .select(listOf(table.columnView.revision, table.columnView.createTime) + columns)
        .run {
            val predicates = mutableListOf<Op<Boolean>>()
            if (until != null)
                predicates.add(table.columnView.revision lessEq until)
            if (auxWhere != null)
                predicates.add(auxWhere)
            if (predicates.isNotEmpty()) {
                where(predicates.reduce {
                    acc, next -> acc and next
                })
            } else
                this
        }
        .orderBy(table.columnView.revision to SortOrder.DESC)
        .limit(limit)
        .map(evaluateRow)
}

internal class DataGoneException: RuntimeException {
    constructor() : super()
    constructor(cause: Throwable?) : super(cause)
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}

private data class _Table(val tableView: Table) {
    init {
        require(tableView is HasRevisionAndCreateTime)
    }
    val columnView: HasRevisionAndCreateTime
        get() = tableView as HasRevisionAndCreateTime
}
private val Table.asTable
    get() = _Table(this)
private val DataTable = DataObjects.asTable
private val ConfigTable = ConfigObjects.asTable

internal interface Cacheable<Map, Object> {
    val data: Map
    val state: Object
}

internal class CachedData(
    override val data: FullData,
    override val state: DataStore
): Cacheable<FullData, DataStore> {
    companion object {
        operator fun invoke(data: FullData) =
            CachedData(data, DataStore().applyData(data))
    }
}
internal class CachedConfig(
    override val data: ConfigMap,
    override val state: Config
): Cacheable<ConfigMap, Config> {
    companion object {
        operator fun invoke(data: ConfigMap) =
            CachedConfig(data, data.createObject())
    }
}

private val objectTypeForTable =
    mapOf(
        DataTable to ObjectType.DATA,
        ConfigTable to ObjectType.CONFIG
    )

private operator fun <T: Any> ResultSet.get(c: Column<T>): T = c.columnType.valueFromDB(getObject(c.name))!!
@JvmName("getNullable")
private operator fun <T> ResultSet.get(c: Column<T?>): T? = c.columnType.valueFromDB(getObject(c.name))
