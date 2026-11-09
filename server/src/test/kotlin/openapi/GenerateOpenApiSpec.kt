package com.moshy.drugcalc.server.openapi

import com.moshy.ProxyMap
import com.moshy.containers.ListAsSortedSet
import com.moshy.drugcalc.calc.calc.Evaluator
import com.moshy.drugcalc.calc.datacontroller.DataController
import com.moshy.drugcalc.server.http.configureModule
import com.moshy.drugcalc.server.http.routing.util.openApiBaseDoc
import com.moshy.drugcalc.server.http.routing.util.openApiRoutingSource
import com.moshy.drugcalc.server.http.user.UserService
import com.moshy.drugcalc.server.io.JsonWithLenientIsoDuration
import com.moshy.drugcalc.server.util.AppConfig
import com.moshy.drugcalc.types.dataentry.BlendName
import com.moshy.drugcalc.types.dataentry.BlendValue
import com.moshy.drugcalc.types.dataentry.CompoundBase
import com.moshy.drugcalc.types.dataentry.CompoundInfo
import com.moshy.drugcalc.types.dataentry.CompoundName
import com.moshy.drugcalc.types.dataentry.FrequencyName
import com.moshy.drugcalc.types.dataentry.FrequencyValue
import com.moshy.drugcalc.types.datasource.DBConfig
import com.moshy.drugcalc.types.datasource.DataSourceDelegate
import io.ktor.server.testing.runTestApplication
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.time.Instant

/**
 * Regenerates `src/main/resources/openapi/documentation.yaml` from the Ktor routing tree.
 *
 * Run with `./gradlew :server:generateOpenApiSpec`.
 */
fun main(args: Array<String>) {
    require(args.size == 1) {
        "usage: generateOpenApiSpec <output-file>"
    }
    val output = Path.of(args.single())

    runBlocking {
        runTestApplication {
            application {
                configureModule(
                    config = AppConfig.App(
                        db = DBConfig("", ""),
                        http = AppConfig.Http(),
                        jwt = AppConfig.Jwt(audience = "-dc-openapi-", issuer = "http://dc-openapi:mem/"),
                    ),
                    userService = UserService(),
                    dataController = DataController(DataController.Config(), UnusedDataSource),
                    evaluator = Evaluator(Evaluator.Config(nThreads = 1)),
                    // The docs UI is irrelevant for spec generation and would write into server/docs.
                    installApiDocs = false,
                )
            }
            startApplication()

            val spec = openApiRoutingSource(JsonWithLenientIsoDuration)
                .read(application, openApiBaseDoc())
                .content

            output.parent?.toFile()?.mkdirs()
            output.toFile().writeText(spec)
        }
    }
}

/**
 * The routing handlers are never invoked during spec generation, so no data source operations are
 * required. This keeps generation independent of a database (or Docker).
 */
private object UnusedDataSource : DataSourceDelegate {
    override suspend fun updateCompounds(compounds: Map<CompoundName, ProxyMap<CompoundInfo>>): Boolean =
        unsupported()

    override suspend fun getCompoundNames(): ListAsSortedSet<CompoundBase> = unsupported()

    override suspend fun getVariantsForCompound(forName: CompoundBase): ListAsSortedSet<String>? = unsupported()

    override suspend fun getCompounds(names: Collection<CompoundName>): Map<CompoundName, CompoundInfo> = unsupported()

    override suspend fun getBlendNames(): ListAsSortedSet<BlendName> = unsupported()

    override suspend fun getBlends(names: Collection<BlendName>): Map<BlendName, BlendValue> = unsupported()

    override suspend fun getFrequencyNames(): ListAsSortedSet<FrequencyName> = unsupported()

    override suspend fun getFrequencies(names: Collection<FrequencyName>): Map<FrequencyName, FrequencyValue> =
        unsupported()

    override suspend fun putBulk(
        compounds: Map<CompoundName, CompoundInfo>,
        blends: Map<BlendName, BlendValue>,
        frequencies: Map<FrequencyName, FrequencyValue>,
    ): Boolean = unsupported()

    override suspend fun deleteBulk(
        compounds: Collection<CompoundName>,
        blends: Collection<BlendName>,
        frequencies: Collection<FrequencyName>,
    ): Boolean = unsupported()

    override suspend fun isDbEmpty(): Boolean = unsupported()

    override suspend fun getCompoundNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<CompoundName> =
        unsupported()

    override suspend fun getBlendNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<BlendName> =
        unsupported()

    override suspend fun getFrequencyNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<FrequencyName> =
        unsupported()

    private fun unsupported(): Nothing = error("data source is not available during OpenAPI generation")
}
