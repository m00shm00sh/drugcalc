package com.moshy.drugcalc.http

import com.moshy.ProxyMap
import com.moshy.drugcalc.calc.Config
import com.moshy.drugcalc.calc.CycleDescription
import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.db.*
import com.moshy.drugcalc.io.*
import com.moshy.drugcalc.misc.DecodedXYList
import com.moshy.drugcalc.repo.Repository
import com.moshy.drugcalc.repo.RepositoryTest
import com.moshy.drugcalc.testutil.assertSetsAreEqual
import com.moshy.drugcalc.testutil.assertAll as co_assertAll
import com.moshy.drugcalc.util.AppConfig
import com.moshy.drugcalc.util.prepareDatabase
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertNotNull
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class CalcApiEndpointsTest {
    @Test
    fun testBasic() = testApplication {
        application {
            httpMainSetup(sharedR2State, AppConfig.Limits()).invoke(this)
        }
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val result = assertDoesNotThrow("applied data diff") {
            client.post("/api/calc") {
                contentType(ContentType.Application.Json)
                setBody(
                    CycleRequest(
                        data = goodDataDiff,
                        config = goodConfigDiff,
                        cycle = listOf(
                            CycleDescription(C_FRED, 100.0, ONE_DAY * 0, ONE_DAY * 7, F_NAME)
                        )
                    )
                )
            }.toResultOrException<Map<String, DecodedXYList>>()
        }
        co_assertAll(
            { assertSetsAreEqual(setOf("fred"), result.keys) },
            {
                val fredVals = result["fred"]
                assertNotNull(fredVals)
                val xs = fredVals.x
                val ys = fredVals.y
                assertAll(
                    { assertEquals(ONE_DAY * 0.5, xs[1] - xs[0], "applied config diff") },
                    { assertEquals(100.0, ys[0]) }
                )
            }
        )
    }

    private companion object {
        val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)
        const val C_FRED = "fred foo"
        val goodConfigDiff = ProxyMap<Config>("tickDuration" to ONE_DAY * 0.5)
        const val F_NAME = "dada daily"
        val goodDataDiff = DiffData(
            compounds = Diff(
                add = mapOf(C_FRED to CompoundInfo(ONE_DAY))
            ),
            frequencies = Diff(
                add = mapOf(F_NAME to listOf(ONE_DAY))
            )
        )

        /* This is copied from RepositoryTest. The important detail here is we only need to call init1 without saving
         * variables then check shape of data (i.e. correct types and nothing that shouldn't be empty is empty)
         */
        val dbs = mutableListOf<Database>()
        val repos = mutableListOf<Repository>()

        val repoConfig = AppConfig.Repo(readOnlyMode = true,
            AppConfig.Repo.CacheEvictionPolicy(0L),
            AppConfig.Repo.CacheEvictionPolicy(0L)
        )

        @JvmStatic
        fun init1(): Int {
            val db = Database.connect(HikariDataSource().apply {
                jdbcUrl = "jdbc:h2:mem:testdb-CalcApiTest-${dbs.size};DB_CLOSE_DELAY=-1"
            })
            // if this throws, don't bother letting junit making the failure pretty because we have the log for that
            prepareDatabase(db, repoConfig.copy(readOnlyMode = false))
            synchronized(this) {
                val repo = Repository(repoConfig, db)
                dbs.add(db)
                repos.add(repo)
                return dbs.size - 1
            }
        }

        @JvmStatic
        fun init2(): Int {
            synchronized(this) {
                val idx = init1()
                RepositoryTest.dbInit2(dbs[idx])
                return idx
            }
        }

        val sharedR1State by lazy {
            val idx = init1()
            repos[idx]
        }

        val sharedR2State by lazy {
            RepositoryTest.initAll()
            sharedR1State
            val idx = init2()
            repos[idx]
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