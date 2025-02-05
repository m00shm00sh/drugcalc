package com.moshy.drugcalc.http

import com.moshy.ProxyMap
import com.moshy.drugcalc.calc.Config
import com.moshy.drugcalc.db.*
import com.moshy.drugcalc.io.ConfigMap
import com.moshy.drugcalc.io.RevisionSummary
import com.moshy.drugcalc.repo.Repository
import com.moshy.drugcalc.repo.RepositoryTest
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
import io.ktor.server.routing.*
import io.ktor.server.testing.*
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

internal class ConfigApiEndpointsTest {
    @Test
    fun getLatestConfigRevision() = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.get("/api/config/latest")
        co_assertAll(
            { assertEquals(HttpStatusCode.OK, response.status) },
            { assertDoesNotThrow {
                val map = response.body<ConfigMap>()
                assertTrue(map.isNotEmpty())
            } }
        )
    }
    @Test
    fun getSpecificConfigRevision() = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.get("/api/config/1")
        co_assertAll(
            { assertEquals(HttpStatusCode.OK, response.status) },
            { assertDoesNotThrow {
                val map = response.body<ConfigMap>()
                assertTrue(map.isNotEmpty())
            } }
        )
    }

    @Test
    fun getMissingConfigRevision() = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val response = client.get("/api/config/1234567890")
        co_assertAll(
            { assertEquals(HttpStatusCode.NotFound, response.status) },
            { assertContains("revision 1234567890 not found", response.bodyAsText()) }
        )
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForGetInvalidConfigRevision")
    fun getInvalidConfigRevision(name: String, a: CheckArg<Any, String>) = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        a.invokeSuspend {
            val response = client.get("/api/config/$this")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            throw Throwable(response.bodyAsText())
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForCheckConfig")
    fun checkConfig(name: String, a: CheckArg<Int, ConfigMap>) = testApplication {
        application {
            httpMainSetup(sharedR1State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        a.invokeSuspend {
            client.post("/api/config/1/diff") {
                contentType(ContentType.Application.Json)
                setBody(this@invokeSuspend)
            }.toResultOrException<Int>()
        }
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForDiffRev")
    fun diffRev(name: String, a: CheckArg<ConfigMap, String>) = testApplication {
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
                .get("/api/config/$this/diff")
                .toResultOrException<ConfigMap>()
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
                .get(constructDiffstatQueryString("config", this@invokeSuspend))
                .toResultOrException<List<RevisionSummary.Config>>()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForCheckConfig")
    fun adminUpdate(name: String, a: CheckArg<Int, ConfigMap>) = testApplication {
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
            client.post("/api/config/admin/update") {
                contentType(ContentType.Application.Json)
                setBody(this@invokeSuspend)
            }.toResultOrException<Int>()
        }

        if (test2) {
            assertDoesNotThrow {
                val response2 = client.get("/api/config/latest")
                val cfg = response2.body<ConfigMap>().createObject()
                assertEquals(goodConfigDiff["tickDuration"], cfg.tickDuration)
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
        val response = client.get("/api/config/admin/undo")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    private companion object {
        val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)
        lateinit var configItemNames: Set<String>
        fun isConfigItemNamesInitialized() = ::configItemNames.isInitialized
        val goodConfigDiff = ProxyMap<Config>("tickDuration" to ONE_DAY * 2)
        val badConfigDiff = ProxyMap<Config>("cutoff" to 0.0)
        lateinit var r2ConfigChangedNames: Set<String>
        fun isr2ConfigChangedNamesInitialized() = ::r2ConfigChangedNames.isInitialized

        @JvmStatic
        fun testArgsForGetInvalidConfigRevision() =
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
        fun testArgsForCheckConfig() =
            listOf(
                arrayOf("success",
                    CheckArg.nothrow<Int, _>(goodConfigDiff) { assertEquals(1, it) }
                ),
                arrayOf("failure (field)",
                    CheckArg.throws<IllegalArgumentException, _>(badConfigDiff, badConfigDiff.keys.first())
                ),
                arrayOf("failure (empty)",
                    CheckArg.throws<IllegalArgumentException, _>(ConfigMap(), "empty diff")
                )
            ).map { Arguments.of(*it) }

        @JvmStatic
        fun testArgsForDiffRev() =
            listOf(
                arrayOf("success",
                    CheckArg.nothrow<ConfigMap, String>("2")
                        { assertSetsAreEqual(r2ConfigChangedNames, it.keys) } // diff, not full
                ),
                arrayOf("404(+)",
                    CheckArg.throws<NoSuchElementException, String>
                        ("1234567890", Regex("revision 1234567890\\b.*\\bnot found"))
                ),
                arrayOf("404(-)",
                    CheckArg.throws<NoSuchElementException, String>
                        ("-1", Regex("revision -1\\b.*\\bnot found"))
                ),
                arrayOf("400",
                    CheckArg.throws<IllegalArgumentException, String>
                        ("fred")
                )
            ).map { Arguments.of(*it) }

        @JvmStatic
        fun testArgsForDiffStat() =
            testArgsForDiffStatFactory<RevisionSummary.Config> { it.modified != 0 }

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
                if (tv != null && !isConfigItemNamesInitialized()) {
                    @Suppress("UNCHECKED_CAST") val cfgItems = tv["ConfigMap"] as ConfigMap
                    configItemNames = cfgItems.keys
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
                if (tv != null && !isr2ConfigChangedNamesInitialized()) {
                    @Suppress("UNCHECKED_CAST")
                    r2ConfigChangedNames = (tv["2/ConfigMapDiff"] as ConfigMap).keys
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