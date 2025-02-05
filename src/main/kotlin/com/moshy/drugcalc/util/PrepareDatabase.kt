package com.moshy.drugcalc.util

import com.moshy.drugcalc.calc.Config
import com.moshy.drugcalc.calcdata.*
import com.moshy.drugcalc.db.Caches
import com.moshy.drugcalc.db.ConfigObjects
import com.moshy.drugcalc.db.DataObjects
import com.moshy.drugcalc.db.ObjectType
import com.moshy.drugcalc.io.*
import com.moshy.drugcalc.io.Diff
import com.moshy.drugcalc.io.DiffData
import com.moshy.drugcalc.io.FullData
import com.moshy.drugcalc.io.JsonWithLenientIsoDuration
import com.moshy.drugcalc.misc.caseFolded
import com.moshy.drugcalc.misc.withPreparedCompounds
import kotlinx.datetime.Clock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/* NOTES:
 * (1) this isn't just for Main; the Repo tests use the seed data to create the test db (and pass a non-null [testVars]
 * (2) there is heavy back and forth between diff and full map. This is inefficient but it doesn't matter because it's
 *     only done once.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun prepareDatabase(
    db: Database,
    repoConfig: AppConfig.Repo,
    // expose the DiffData, FullData, ConfigMap, Config states for testing
    testVars: MutableMap<String, Any>? = null
): AppConfig.Repo {
    val logger = LoggerFactory.getLogger("PrepareDB")
    transaction(db) {
        if (!repoConfig.readOnlyMode)
            SchemaUtils.create(DataObjects, ConfigObjects, Caches)
        val dataRowCount = DataObjects.select(DataObjects.revision).count()
        val configRowCount = ConfigObjects.select(ConfigObjects.revision).count()
        if (dataRowCount == 0L) run {
            logger.debug("No data rows")
            check(Caches.select(Caches.revision).where { Caches.objType eq ObjectType.DATA }.count() == 0L) {
                logger.error("Found cache[data] row")
                "Database in bad state: no data rows but nonempty cache[data] row"
            }
            if (repoConfig.readOnlyMode)
                throw IllegalStateException("readOnly mode enabled but ${DataObjects.tableName} table empty")

            val data = try {
                File("./data/init-data.json").inputStream().use {
                    JsonWithLenientIsoDuration.decodeFromStream<ConfigJsonSchema>(it)
                }.run {
                    listOf(::compounds, ::blends, ::frequencies)
                        .map {
                            require(it.get().isNotEmpty()) {
                                "${it.name} is empty"
                            }
                            Diff(add = it.get())
                        }
                        .let @Suppress("UNCHECKED_CAST") {
                            /* all params are optional so using reflection to bypass static typing
                             * is more hassle than it's worth since it entails building a callBy map
                             */
                            DiffData(
                                compounds = it[0] as Diff<CompoundInfo>,
                                blends = it[1] as Diff<BlendValue>,
                                frequencies = it[2] as Diff<FrequencyValue>
                            )
                        }
                }.run {
                    caseFolded().withPreparedCompounds()
                }.run {
                    FullDiffData(
                        compounds = compounds.toFullDiff(emptyMap(), ""),
                        blends = blends.toFullDiff(emptyMap(), ""),
                        frequencies = frequencies.toFullDiff(emptyMap(), "")
                    )
                }.saveTestVar(testVars, "FDiffData")
            } catch (e: IOException) {
                logger.error("couldn't open init data file: ${e.message}")
                throw e
            }
            DataObjects.insert {
                it[revision] = 1
                it[createTime] = Clock.System.now().saveTestVar(testVars, "DataCTime")
                it[diffCompounds] = data.compounds
                it[diffBlends] = data.blends
                it[diffFrequencies] = data.frequencies
            }
            Caches.insert {
                it[objType] = ObjectType.DATA
                it[revision] = 1
                it[Caches.data] = Json.encodeToString(
                    FullData(data.compounds.add, data.blends.add, data.frequencies.add)
                        .saveTestVar(testVars, "FullData")
                )
            }
        }
        if (configRowCount == 0L) run {
            check(Caches.select(Caches.revision).where { Caches.objType eq ObjectType.CONFIG }.count() == 0L) {
                "Database in bad state: no config rows but nonempty cache[config] row"
            }
            if (repoConfig.readOnlyMode)
                throw IllegalStateException("readOnly mode enabled but ${ConfigObjects.tableName} table empty")

            val cfg = try {
                File("./data/init-cfg.json").inputStream().use {
                    JsonWithLenientIsoDuration.decodeFromStream<Config>(it)
                }.run {
                    ConfigMap(this)
                }.saveTestVar(testVars, "ConfigMap")
            } catch (e: IOException) {
                logger.error("couldn't open init config file: ${e.message}")
                throw e
            }
            ConfigObjects.insert {
                it[revision] = 1
                it[createTime] = Clock.System.now().saveTestVar(testVars, "ConfigCTime")
                it[config] = cfg
            }
            Caches.insert {
                it[objType] = ObjectType.CONFIG
                it[revision] = 1
                it[data] = Json.encodeToString(
                    cfg.createObject()
                        .saveTestVar(testVars, "Config")
                )
            }
        }
    }
    return repoConfig
}

@Serializable
private data class ConfigJsonSchema(
    val compounds: CompoundsMap = emptyMap(),
    val blends: BlendsMap = emptyMap(),
    val frequencies: FrequenciesMap = emptyMap()
)

private fun <T: Any> T.saveTestVar(m: MutableMap<String, Any>?, key: String) = apply { m?.put(key, this) }