package com.moshy.drugcalc.repo

import com.moshy.drugcalc.calc.Config
import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.calcdata.DataStore
import com.moshy.drugcalc.db.*
import com.moshy.drugcalc.io.*
import com.moshy.drugcalc.misc.caseFolded
import com.moshy.drugcalc.misc.reversed
import com.moshy.drugcalc.misc.summarized
import com.moshy.drugcalc.misc.withPreparedCompounds
import com.moshy.drugcalc.testutil.CheckArg
import com.moshy.drugcalc.testutil.assertAll as co_assertAll
import com.moshy.drugcalc.testutil.assertContains
import com.moshy.drugcalc.testutil.assertDoesNotContain
import com.moshy.drugcalc.util.AppConfig
import com.moshy.drugcalc.util.AppConfig.Repo.CacheEvictionPolicy
import com.moshy.drugcalc.util.prepareDatabase
import com.moshy.ProxyMap
import com.moshy.containers.transpose
import com.zaxxer.hikari.HikariDataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

internal class RepositoryTest {

    @Test
    fun getLatestData() = runTest {
        val repo = sharedR1State
        val data = assertDoesNotThrow { repo.getLatestData() }
        assertAll(
            { assertEquals(1, data.first) },
            { assertEquals(seed1.fullData, data.second.data) },
            { assertEquals(seed1.dataObj, data.second.state) }
        )
    }

    @Test
    fun getLatestConfig() = runTest {
        val repo = sharedR1State
        val config = assertDoesNotThrow { repo.getLatestConfig() }
        assertAll(
            { assertEquals(1, config.first) },
            { assertEquals(seed1.configMap, config.second.data) },
            { assertEquals(seed1.configObj, config.second.state) }
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForUpdateLatestDataObject")
    fun updateLatestDataObject(name: String, a: CheckArg<Unit, DiffData>) = runTest {
        val repo = copiedR1State()
        a.invokeSuspend {
            val rev = repo.updateLatestDataObject(this)

            /* Because we're reusing the same parameter set for updateLatestDataObject and checkDataDiff, we have
             * to do the assertions inside the invokee lambda.
             */
            val (rev_, data) = repo.getLatestData()
            assertAll(
                { assertEquals(rev, rev_) },
                { assertFalse(C_T_CYP in data.state.compounds) },
                { assertTrue(C_FRED in data.state.compounds) }
            )
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForUpdateLatestConfigObject")
    fun updateLatestConfigObject(name: String, a: CheckArg<Unit, ConfigMap>) = runTest {
        val repo = copiedR1State()
        a.invokeSuspend {
            val rev = repo.updateLatestConfigObject(this)
            assertEquals(2, rev)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForUpdateLatestDataObject")
    fun checkDataDiff(name: String, a: CheckArg<Int, DiffData>) = runTest {
        val repo = sharedR1State
        a.invokeSuspend {
            repo.checkDataDiff(this)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForUpdateLatestConfigObject")
    fun checkConfigDiff(name: String, a: CheckArg<Int, ConfigMap>) = runTest {
        val repo = sharedR1State
        a.invokeSuspend {
            repo.checkConfigDiff(this)
        }
    }

    class GetRevisionsP(val until: Int? = null, val limit: Int? = null)

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForGetDataRevisions")
    fun getDataRevisions(name: String, a: CheckArg<List<RevisionSummary.Data>, GetRevisionsP>) = runTest {
        val repo = sharedR2State
        a.invokeSuspend { repo.getDataRevisions(until, limit) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForGetConfigRevisions")
    fun getConfigRevisions(name: String, a: CheckArg<List<RevisionSummary.Config>, GetRevisionsP>) = runTest {
        val repo = sharedR2State
        a.invokeSuspend { repo.getConfigRevisions(until, limit) }
    }

    @Test
    fun getDataDiffForRevision_f() = runTest {
        val repo = sharedR2State
        val t = assertThrows<NoSuchElementException>{ repo.getDataDiffForRevision(123456789) }
        assertEquals("revision 123456789 not found", t.message)
    }

    @Test
    fun getConfigDiffForRevision_f() = runTest {
        val repo = sharedR2State
        val t = assertThrows<NoSuchElementException>{ repo.getConfigDiffForRevision(123456789) }
        assertEquals("revision 123456789 and its predecessor not found", t.message)
    }
    @Test
    fun getDataDiffForRevision() = runTest {
        val repo = sharedR2State
        val data = assertDoesNotThrow { repo.getDataDiffForRevision(1) }
        assertEquals(seed1.fDiffData, data)
    }

    @Test
    fun getConfigDiffForRevision() = runTest {
        val repo = sharedR2State
        val config = assertDoesNotThrow { repo.getConfigDiffForRevision(1) }
        assertEquals(seed1.configMap, config)
    }

    @Test
    fun getDataAtRevision() = runTest {
        val repo = sharedR2State
        val data = assertDoesNotThrow { repo.getDataAtRevision(1) }
        assertAll(
            { assertEquals(seed1.fullData, data.data) },
            { assertEquals(seed1.dataObj, data.state) }
        )
    }

    @Test
    fun getConfigAtRevision() = runTest {
        val repo = sharedR2State
        val config = assertDoesNotThrow { repo.getConfigAtRevision(1) }
        assertAll(
            { assertEquals(seed1.configMap, config.data) },
            { assertEquals(seed1.configObj, config.state) }
        )
    }

    @Test
    fun undoLatestData() = runTest {
        val repo = copiedR1State()
        assertDoesNotThrow { repo.undoLatestData() }
        val rev = repo.getDataDiffForRevision(1)
        val new = repo.getLatestData()
        co_assertAll(
            { assertEquals(2, new.first) },
            // the seed data produces one row, thus the undo of that produces an empty state
            { assertTrue(new.second.data.allProps { it.isEmpty() }) },
            {
                val diff = repo.getDataDiffForRevision(2)
                assertEquals(rev, diff.reversed())
            }
        )
    }

    @Test
    fun undoLatestConfig() = runTest {
        val repo = copiedR1State()
        assertThrows<UnsupportedOperationException> { repo.undoLatestConfig() }
    }

    @Suppress("ClassName")
    internal companion object {
        private val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)
        const val C_FRED = "fred"
        private const val B_FRED = "fred"
        private const val F_FRED = "fred"
        const val C_T_CYP = "testosterone cypionate"
        const val C_T_PROP = "testosterone propionate"
        private const val B_SUS = "sustanon 250"
        private const val F_ED = "every day"
        /* There is a consistent difference between value going into and value coming out of SQL.
         * Depending on system load, this can be as high as a whole second.
         * I'm unsure of getting Junit to retry with exponential increase in the sense of the first attempt being
         * 50, then 100, and so on up to 800.
         * As long as revision 2 number is after revision 1, this shouldn't be a problem 🤞
         */
        private const val TIME_TOLERANCE_MSEC = 1500L

        var sharedR1 = -1
        var sharedR2 = -1

        private object seed1 {
            lateinit var fDiffData: FullDiffData
            lateinit var fullData: FullData
            lateinit var dataObj: DataStore
            lateinit var configObj: Config
            lateinit var configMap: ConfigMap
            lateinit var dataCTime: Instant
            lateinit var configCTime: Instant

            fun didInit() = ::fDiffData.isInitialized
        }
        private object seed2 {
            lateinit var dataCTime: Instant
            lateinit var dataFDiffCompounds: FullDiff<CompoundInfo>
            lateinit var configCTime: Instant
            lateinit var configMap: ConfigMap

            fun didInit() = ::dataCTime.isInitialized
        }

        private val dataDiffs =
            listOf(
                DiffData( // good
                    parentRevision = 1,
                    compounds = Diff(
                        delete = setOf(C_T_CYP),
                        add = mapOf(C_FRED to CompoundInfo(ONE_DAY))

                    )
                ),
                // FIXME: coverage -- bad diff -- invalid revision
                DiffData( // bad
                    compounds = Diff(
                        delete = setOf(C_FRED),
                        add = mapOf(C_T_PROP to CompoundInfo(ONE_DAY))
                    )
                ),
            ).map { it.caseFolded().withPreparedCompounds() }
        private val goodDataDiff = dataDiffs[0]
        private val badDataDiff = dataDiffs[1]
        private val goodConfigDiff = ProxyMap<Config>("tickDuration" to ONE_DAY)
        private val badConfigDiff = ProxyMap<Config>("cutoff" to 0.0)

        private fun verifyDataPreconditions(repo: Repository) {
            val ld = runBlocking { repo.getLatestData() }
            val ds = ld.second.state
            assertEquals(1, ld.first)
            assertDoesNotContain(ds.compounds, C_FRED)
            assertDoesNotContain(ds.blends, B_FRED)
            assertDoesNotContain(ds.frequencies, F_FRED)
            assertContains(ds.compounds, C_T_CYP)
            assertContains(ds.compounds, C_T_PROP)
            assertContains(ds.blends, B_SUS)
            assertContains(ds.frequencies, F_ED)
        }

        @JvmStatic
        private fun testArgsForUpdateLatestDataObject() =
            listOf(
                arrayOf("failure",
                    CheckArg.throws<IllegalArgumentException, _>(badDataDiff, "compound")
                ),
                arrayOf("success",
                    CheckArg.nothrow(goodDataDiff)
                ),
                // coverage for checkDataDiff given non-null revision
                arrayOf("success (2)",
                    CheckArg.nothrow(goodDataDiff.copy(parentRevision = 1))
                )
            ).map { Arguments.of(*it) }

        @JvmStatic
        private fun testArgsForUpdateLatestConfigObject() =
            listOf(
                arrayOf("failure",
                    CheckArg.throws<IllegalArgumentException, _>(badConfigDiff, badConfigDiff.keys.first())
                ),
                arrayOf("success",
                    CheckArg.nothrow(goodConfigDiff)
                )
                // (ConfigDiff with parentRevision cannot exist; we do not need a second successful test)
            ).map { Arguments.of(*it) }

        @JvmStatic
        private fun <T: RevisionSummary, U> testArgsForGetRevisionsFactory(expected: List<U>) =
            listOf(
                arrayOf("(u=1, lim=1) => [r1]",
                    CheckArg.nothrow<List<T>, _>(GetRevisionsP(until = 1, limit = 1)) {
                        assertAll(
                            { assertEquals(1, it.size) },
                            { assertEquals(expected[1] as Any, it[0]) }
                        )
                    }
                ),
                arrayOf("(u=2, lim=1) => [r2]",
                    CheckArg.nothrow<List<T>, _>(GetRevisionsP(until = 2, limit = 1)) {
                        assertAll(
                            { assertEquals(1, it.size) },
                            { assertEquals(expected[0] as Any, it[0]) }
                        )
                    }
                ),
                arrayOf("(u=2, lim=2) => [r2, r1]",
                    CheckArg.nothrow<List<T>, _>(GetRevisionsP(until = 2, limit = 2)) {
                        assertAll(
                            { assertEquals(2, it.size) },
                            { assertEquals(expected as Any, it) }
                        )
                    }
                ),
                arrayOf("(u<0)",
                    CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(until = -1), "until")
                ),
                arrayOf("(u=0)",
                    CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(until = 0), "until")
                ),
                arrayOf("(l<0)",
                    CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(limit = -1), "limit")
                ),
                arrayOf("(l=0)",
                    CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(limit = 0), "limit")
                ),
            ).map { Arguments.of(*it) }

        @JvmStatic
        private fun testArgsForGetDataRevisions() =
            testArgsForGetRevisionsFactory<RevisionSummary.Data, _>(dataRevisions)

        @JvmStatic
        private fun testArgsForGetConfigRevisions() =
            testArgsForGetRevisionsFactory<RevisionSummary.Config, _>(configRevisions)

        // Wrapper for RevisionSummary types to handle tolerance in temporal matching
        @Suppress("EqualsOrHashCode")
        private class R(val kc: KClass<out RevisionSummary>, rev: Int, ts: Instant, a: Int, b: Int, vararg other: Any?) {
            val r: RevisionSummary = kc.primaryConstructor!!.call(rev, ts, a, b, *other)
            override fun toString() = "$r (timeTol=$TIME_TOLERANCE_MSEC)"
            override fun equals(other: Any?): Boolean {
                if (other == null || other::class != kc) return false
                if (r == other) return true
                other as RevisionSummary
                return areInstantsEqual(r.createTime, other.createTime, TIME_TOLERANCE_MSEC)
            }
        }

        private val dataRevisions: List<R> by lazy {
            require(seed1.didInit())
            require(seed2.didInit())

            buildList(2) {
                // build in descending order
                val expValsR2 =
                    FullDiffData(compounds = seed2.dataFDiffCompounds)
                        .mapAll { it.summarized().toList() }
                        .transpose { it.sum() }
                val expValsR1 =
                    seed1.fDiffData
                        .mapAll { it.summarized().toList() }
                        .transpose { it.sum() }
                add(R(RevisionSummary.Data::class, 2, seed2.dataCTime, expValsR2[0], expValsR2[1], 1))
                add(R(RevisionSummary.Data::class, 1, seed1.dataCTime, expValsR1[0], expValsR1[1], null))
            }
        }
        private val configRevisions: List<R> by lazy {
            require(seed1.didInit())
            require(seed2.didInit())
            buildList(2) {
                // build in descending order
                val cm2 = seed2.configMap
                val cm1 = seed1.configMap
                val expValR2 = cm2.size
                val expValR1 = cm1.size
                add(R(RevisionSummary.Config::class, 2, seed2.configCTime, expValR2, (cm2 - cm1).size))
                add(R(RevisionSummary.Config::class, 1, seed1.configCTime, expValR1, expValR1))
            }
        }

        private val dbs = mutableListOf<Database>()
        private val repos = mutableListOf<Repository>()

        @JvmStatic
        private fun init1(saveVars: MutableMap<String, Any>? = null): Int {
            synchronized(this) {
                val db = Database.connect(HikariDataSource().apply {
                    jdbcUrl = "jdbc:h2:mem:testdb-RepoTest-${dbs.size};DB_CLOSE_DELAY=-1"
                })
                val repoConfig = AppConfig.Repo(false, CacheEvictionPolicy(0L), CacheEvictionPolicy(0L))

                // if this throws, don't bother letting junit making the failure pretty because we have the log for that
                prepareDatabase(db, repoConfig, saveVars)

                var repoConfigRO = false
                if (saveVars != null && !seed1.didInit()) {
                    seed1.fDiffData = saveVars["FDiffData"] as FullDiffData
                    seed1.fullData = saveVars["FullData"] as FullData
                    seed1.dataObj = runBlocking { CachedData(seed1.fullData).state }

                    seed1.configObj = saveVars["Config"] as Config
                    seed1.configMap = saveVars["ConfigMap"] as ProxyMap<*> castTo Config::class

                    seed1.dataCTime = saveVars["DataCTime"] as Instant
                    seed1.configCTime = saveVars["ConfigCTime"] as Instant
                    repoConfigRO = true
                }
                val repo = Repository(repoConfig.copy(readOnlyMode = repoConfigRO), db)
                dbs.add(db)
                repos.add(repo)
                return dbs.size - 1
            }

        }


        @JvmStatic
        fun dbInit2(db: Database, saveVars: MutableMap<String, Any>? = null) {
            check(seed1.didInit()) {
                "must call init1() first"
            }
            val ds2 = seed1.dataObj.run { applyDiff(dataDiffs[0].toFullDiffData(this)) }
            goodConfigDiff.saveTestVar(saveVars, "2/ConfigMapDiff")
            val cfg2 = (seed1.configMap + goodConfigDiff)
                .saveTestVar(saveVars, "2/ConfigMap")
            // prepareDatabase for revision 2 contents
            blockingTransaction(db) {
                DataObjects.insert {
                    it[revision] = 2
                    it[createTime] = Clock.System.now().saveTestVar(saveVars, "2/DataCTime")
                    it[diffCompounds] =
                        dataDiffs[0].compounds
                            .toFullDiff(seed1.dataObj.compounds, "")
                            .saveTestVar(saveVars, "2/DataFDiff/Compounds")
                }
                // a batch update is no good because the selection criteria are nonuniform
                Caches.update(
                    where = {
                        (Caches.revision eq 1) and
                                (Caches.objType eq ObjectType.DATA)
                    }
                ){
                    it[revision] = 2
                    it[data] = Json.encodeToString(ds2.toFullData())
                }
                ConfigObjects.insert {
                    it[revision] = 2
                    it[createTime] = Clock.System.now().saveTestVar(saveVars, "2/ConfigCTime")
                    it[config] = cfg2
                }
                Caches.update(
                    where = {
                        (Caches.revision eq 1) and
                                (Caches.objType eq ObjectType.CONFIG)
                    }
                ) {
                    it[revision] = 2
                    it[data] = Json.encodeToString(cfg2.createObject())
                }
            }
        }

        @JvmStatic
        private fun init2(saveVars: MutableMap<String, Any>? = null): Int {
            synchronized(this) {
                val idx = init1() // if we get an NPE, it means init1 wasn't called by itself, which would be bad
                dbInit2(dbs[idx], saveVars)
                if (saveVars != null && !seed2.didInit()) {
                    seed2.dataCTime = saveVars["2/DataCTime"] as Instant
                    seed2.configCTime = saveVars["2/ConfigCTime"] as Instant
                    @Suppress("UNCHECKED_CAST")
                    seed2.dataFDiffCompounds = saveVars["2/DataFDiff/Compounds"] as FullDiff<CompoundInfo>
                    @Suppress("UNCHECKED_CAST")
                    seed2.configMap = saveVars["2/ConfigMap"] as ConfigMap
                }
                return idx
            }
        }

        private val sharedR1State by lazy {
            check(!seed1.didInit())
            sharedR1 = init1(mutableMapOf())
            repos[sharedR1]
        }
        private val sharedR2State by lazy {
            check(!seed2.didInit())
            sharedR2 = init2(mutableMapOf())
            repos[sharedR2]
        }

        @JvmStatic
        private fun copiedR1State() = repos[init1()]

        @JvmStatic
        private fun copiedR2State() = repos[init2()]

        @BeforeAll
        @JvmStatic
        fun initAll() {
            verifyDataPreconditions(sharedR1State)
        }

        @AfterAll
        @JvmStatic
        fun cleanupDbs() {
            /* Exposed doesn't seem to aggressively reuse a connection so we can't pass it a connection-scope h2:mem,
             * thus we need to do drop table cleanup to prevent resource leaks ourselves.
             */
            val names = listOf(DataObjects, ConfigObjects, Caches).map { it.tableName }
            val dropStmt = names.joinToString(separator = "") { "drop table $it;" }
            for (db in dbs)
                blockingTransaction(db) {
                    exec(dropStmt)
                }
        }
    }
}

private fun <T: Any> T.saveTestVar(m: MutableMap<String, Any>?, key: String) = apply { m?.put(key, this) }