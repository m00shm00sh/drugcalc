package com.moshy.drugcalc.http

import com.moshy.drugcalc.calcdata.BlendsMap
import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.calcdata.CompoundsMap
import com.moshy.drugcalc.calcdata.FrequenciesMap
import com.moshy.drugcalc.db.*
import com.moshy.drugcalc.io.*
import com.moshy.drugcalc.repo.Repository
import com.moshy.drugcalc.repo.RepositoryTest
import com.moshy.drugcalc.repo.allProps
import com.moshy.drugcalc.testutil.*
import com.moshy.drugcalc.util.AppConfig
import com.moshy.drugcalc.util.prepareDatabase
import com.moshy.drugcalc.testutil.assertAll as co_assertAll
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class DataApiEndpointsTest {
    @Test
    fun getLatestDataRevision() = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                // TODO: should we parameterize this to test both strict and lenient Duration strings?
                json()
            }
        }
        val response = client.get("/api/data/latest")
        co_assertAll(
            { assertEquals(HttpStatusCode.OK, response.status) },
            { assertDoesNotThrow {
                val data = response.body<FullData>()
                assertTrue(data.allProps(Map<*, *>::isNotEmpty))
            } }
        )
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForGetLatestDataField")
    fun getLatestDataRevisionWithField(field: String, a: CheckArg<Map<String, Any>, Unit>) = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        a.invokeSuspend {
            client
            .get("/api/data/1/field/$field")
            .toResultOrException<Map<String, Any>>(fieldTypeinfos[field] ?: typeInfo<Any>())
        }
    }

    @Test
    fun getSpecificDataRevision() = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.get("/api/data/1")
        co_assertAll(
            { assertEquals(HttpStatusCode.OK, response.status) },
            { assertDoesNotThrow {
                val data = response.body<FullData>()
                assertTrue(data.allProps(Map<*, *>::isNotEmpty))
            } }
        )
    }

    @Test
    fun getMissingDataRevision() = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.get("/api/data/1234567890")
        co_assertAll(
            { assertEquals(HttpStatusCode.NotFound, response.status) },
            { assertContains("revision 1234567890 not found", response.bodyAsText()) }
        )
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForGetInvalidDataRevision")
    fun getInvalidDataRevision(name: String, a: CheckArg<Any, String>) = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        a.invokeSuspend {
            val response = client.get("/api/data/$this")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            throw Throwable(response.bodyAsText())
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForCheckData")
    fun checkData(name: String, a: CheckArg<Int, DiffData>) = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        a.invokeSuspend {
            client
            .post("/api/data/1/diff") {
                contentType(ContentType.Application.Json)
                setBody(this@invokeSuspend)
            }.toResultOrException<Int>()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForDiffRev")
    fun diffRev(name: String, fdd: Boolean, a: CheckArg<HasParentRevision, String>) = testApplication {
        application {
            httpMainSetup(sharedR2State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        a.invokeSuspend {
            client
            .get(buildString {
                append("/api/data/${this@invokeSuspend}/diff")
                if (fdd)
                    append("/full")
            }).run {
                when (fdd) {
                    true -> toResultOrException<FullDiffData>()
                    else -> toResultOrException<DiffData>()
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForDiffStat")
    fun diffStat(name: String, a: CheckArg<List<RevisionSummary>, GetRevisionsP>) = testApplication {
        application {
            httpMainSetup(sharedR2State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        a.invokeSuspend {
            client
            .get(constructDiffstatQueryString("data", this@invokeSuspend))
            .toResultOrException<List<RevisionSummary.Data>>()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForCheckData")
    fun adminUpdate(name: String, a: CheckArg<Int, DiffData>) = testApplication {
        application {
            httpMainSetup(copiedR1State(), AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        var test2 = false
        a.withNewResultValueMatcher {
            test2 = true
            assertEquals(2, it)
        }.invokeSuspend {
            client
            .post("/api/data/admin/update") {
                contentType(ContentType.Application.Json)
                setBody(this@invokeSuspend)
            }.toResultOrException<Int>()
        }

        if (test2) {
            assertDoesNotThrow {
                val response2 = client.get("/api/data/latest")
                val data = response2.body<FullData>()
                assertEquals(ONE_DAY * 2, data.compounds[RepositoryTest.C_FRED]?.halfLife)
            }
        }
    }

    @Test
    fun adminUndo() = testApplication {
        application {
            httpMainSetup(copiedR1State(), AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.post("/api/data/admin/undo")
        assertEquals(HttpStatusCode.NoContent, response.status)
        val fullData2 = assertDoesNotThrow { client.get("/api/data/2").toResultOrException<FullData>() }
        assertTrue(fullData2.allProps { it.isEmpty() })
    }

    private companion object {
        val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)

        lateinit var r1CompoundNames: Set<String>
        fun isR1CompoundNamesInitialized() = ::r1CompoundNames.isInitialized

        private val goodDataDiff =
            DiffData( // good
                compounds = Diff(
                    delete = setOf(RepositoryTest.C_T_CYP),
                    add = mapOf(RepositoryTest.C_FRED to CompoundInfo(ONE_DAY * 2))

                )
            )
        private val badDataDiff =
            DiffData( // bad
                compounds = Diff(
                    delete = setOf(RepositoryTest.C_FRED),
                    add = mapOf(RepositoryTest.C_T_PROP to CompoundInfo(ONE_DAY))
                )
            )

        lateinit var r2CompoundsDiff: FullDiff<CompoundInfo>
        fun isR2CompoundsDiffInitialized() = ::r2CompoundsDiff.isInitialized

        @JvmStatic
        fun testArgsForGetInvalidDataRevision() =
            listOf(
                arrayOf("-1",
                    CheckArg.throws<Throwable, _>("-1", "invalid revision (-1)")
                ),
                arrayOf("0",
                    CheckArg.throws<Throwable, _>("0", "invalid revision (0)")
                ),
                arrayOf("fred",
                    CheckArg.throws<Throwable, _>("fred", "invalid revision (fred)")
                )
            ).map { Arguments.of(*it) }

        @JvmStatic
        fun testArgsForGetLatestDataField() =
            buildList {
                for (name in listOf("compounds", "blends", "frequencies"))
                    add(arrayOf(name, CheckArg.nothrow<Map<String, Any>, Unit>(Unit) { assertTrue(it.isNotEmpty()) }))
                add(arrayOf(
                    "invalid", CheckArg.throws<IllegalArgumentException, Unit>(Unit, "invalid field: invalid")
                ))
            }.map { Arguments.of(*it) }
        @JvmStatic
        fun testArgsForCheckData() =
            listOf(
                arrayOf("success",
                    CheckArg.nothrow<Int, _>(goodDataDiff) { assertEquals(1, it) }
                ),
                arrayOf("failure (field)",
                    CheckArg.throws<IllegalArgumentException, _>(badDataDiff, badDataDiff.compounds.delete.first())
                ),
                arrayOf("failure (empty)",
                    CheckArg.throws<IllegalArgumentException, _>(DiffData(), "empty diff")
                )
            ).map { Arguments.of(*it) }

        @JvmStatic
        fun testArgsForDiffRev() =
            listOf(
                arrayOf("success (fdd=1)",
                    true,
                    CheckArg.nothrow<FullDiffData, String>("2")
                        {
                            /* Use our own assertMapsAreEqual because the error message is prettier than needing to
                             * char-by-char diff two Map<String, V>.toString() representations that would arise from
                             * assertAll(expectedFullDiff<V>, actualFullDiff<V>).
                             */
                            assertAll(
                                { assertMapsAreEqual(r2CompoundsDiff.delete, it.compounds.delete) },
                                { assertMapsAreEqual(r2CompoundsDiff.add, it.compounds.add) }
                            )
                        }
                ),
                arrayOf("success (fdd=0)",
                    false,
                    CheckArg.nothrow<DiffData, String>("2") {
                        assertAll(
                            { assertSetsAreEqual(r2CompoundsDiff.delete.keys, it.compounds.delete.toSet()) },
                            { assertMapsAreEqual(r2CompoundsDiff.add, it.compounds.add) }
                        )
                    }
                ),
                arrayOf("404(+)",
                    false,
                    CheckArg.throws<NoSuchElementException, String>
                        ("1234567890", "revision 1234567890 not found")
                ),
                arrayOf("404(-)",
                    false,
                    CheckArg.throws<NoSuchElementException, String>
                        ("-1", "revision -1 not found")
                ),
                arrayOf("400",
                    false,
                    CheckArg.throws<IllegalArgumentException, String>
                        ("fred")
                )
            ).map { Arguments.of(*it) }

        @JvmStatic
        fun testArgsForDiffStat() =
            testArgsForDiffStatFactory<RevisionSummary.Data> { it.totalDeleted != 0 || it.totalAdded != 0 }

        /* This is copied from RepositoryTest. The important detail here is we only need to call init1 without saving
         * variables then check shape of data (i.e. correct types and nothing that shouldn't be empty is empty)
         */
        val dbs = mutableListOf<Database>()
        val repos = mutableListOf<Repository>()

        val repoConfig = AppConfig.Repo(false,
            AppConfig.Repo.CacheEvictionPolicy(0L),
            AppConfig.Repo.CacheEvictionPolicy(0L)
        )

        val emptyRepo = Repository(repoConfig.copy(readOnlyMode = true), Database.connect("jdbc:h2:mem"))

        @JvmStatic
        fun init1(tv: MutableMap<String, Any>? = null): Int {
            val db = Database.connect(HikariDataSource().apply {
                jdbcUrl = "jdbc:h2:mem:testdb-ConfigApiTest-${dbs.size};DB_CLOSE_DELAY=-1"
            })
            // if this throws, don't bother letting junit making the failure pretty because we have the log for that
            prepareDatabase(db, repoConfig, tv)
            synchronized(this) {
                var repoConfigRO = false
                if (tv != null && !isR1CompoundNamesInitialized()) {
                    val r1Items = tv["FullData"] as FullData
                    r1CompoundNames = r1Items.compounds.keys
                    repoConfigRO = true
                }
                val repo = Repository(repoConfig.copy(readOnlyMode = repoConfigRO), db)
                dbs.add(db)
                repos.add(repo)
                return dbs.size - 1
            }
        }

        @JvmStatic
        fun init2(tv: MutableMap<String, Any>? = null): Int {
            synchronized(this) {
                val idx = init1()
                RepositoryTest.dbInit2(dbs[idx], tv)
                if (tv != null && !isR2CompoundsDiffInitialized()) {
                    @Suppress("UNCHECKED_CAST")
                    r2CompoundsDiff = (tv["2/DataFDiff/Compounds"] as FullDiff<CompoundInfo>)
                }
                return idx
            }
        }

        val sharedR1State by lazy {
            val idx = init1(mutableMapOf())
            repos[idx]
        }

        @JvmStatic
        fun copiedR1State() = repos[init1()]

        val sharedR2State by lazy {
            RepositoryTest.initAll()
            sharedR1State
            val idx = init2(mutableMapOf())
            repos[idx]
        }

        @BeforeAll
        @JvmStatic
        fun initAll() {
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

/* Ktor uses TypeInfo for deserialization in `suspend fun body<T>()`;
 * using kx-serialization KSerializer, we would need to use Json.decodeFromString(serializer, bodyAsText()),
 * which is needlessly verbose.
 */
private val fieldTypeinfos = mapOf(
    "compounds" to typeInfo<CompoundsMap>(),
    "blends" to typeInfo<BlendsMap>(),
    "frequencies" to typeInfo<FrequenciesMap>()
)