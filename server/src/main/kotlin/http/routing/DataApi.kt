package com.moshy.drugcalc.server.http.routing

import com.moshy.containers.assertIsSortedSet
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.calc.datacontroller.DataController
import com.moshy.drugcalc.calc.datacontroller.DataController.Companion.DONT_EXPAND_BLEND_COMPOUNDS
import com.moshy.drugcalc.server.http.plugins.authenticatedAsAdmin
import com.moshy.drugcalc.common.oneOf
import com.moshy.ProxyMap
import com.moshy.drugcalc.calc.calc.getTransformersInfo
import com.moshy.drugcalc.server.http.routing.util.*
import com.moshy.drugcalc.server.http.routing.util.UrlStringSerializer.Companion.encode
import com.moshy.drugcalc.server.util.AccessException
import com.moshy.drugcalc.server.util.AppConfig
import com.moshy.drugcalc.types.calccommand.TransformerInfo
import io.ktor.resources.*
import io.ktor.server.routing.*

@Suppress("PropertyName")
@Resource("data")
internal class DataRoute(val _from: UrlString? = null, val limit: Int? = null) {
    val from: String?
        get() = _from?.value
    @Resource("compounds")
    internal class Compounds(val parent: DataRoute) {
        val from: CompoundBase?
            get() = parent.from?.let(::CompoundBase)

        @Resource("{_compound}")
        internal class Name(val parent: Compounds, val _compound: UrlString) {
            val from: String?
                get() = parent.parent.from

            val compound: CompoundBase
                get() = CompoundBase(_compound.value)

            @Resource("{_variant}")
            internal class Variant(val parent: Name, val _variant: UrlString) {
                val variant: String
                    get() = _variant.value.takeIf { it != "-" } ?: ""
            }
        }
    }

    @Resource("blends")
    internal class Blends(val parent: DataRoute) {
        val from: BlendName?
            get() = parent.from?.let(::BlendName)

        @Resource("{_blend}")
        internal class Name(val parent: Blends, val _blend: UrlString) {
            val blend: BlendName
                get() = BlendName(_blend.value)
        }
    }

    @Resource("frequencies")
    internal class Frequencies(val parent: DataRoute) {
        val from: FrequencyName?
            get() = parent.from?.let(::FrequencyName)

        @Resource("{_frequency}")
        internal class Name(val parent: Frequencies, val _frequency: UrlString) {
            val frequency: FrequencyName
                get() = FrequencyName(_frequency.value)
        }
    }

    @Resource("transformers")
    internal class Transformers(val parent: DataRoute)
}



internal fun Route.configureDataRoutes(
    flags: AppConfig.DevFlags,
    dataController: DataController,
) {
    authenticatedAsAdmin {
        post<DataRoute, Data, Unit> { _, newData ->
            requireOperationSucceeded { dataController.putEntries(newData) }
        }
        delete<DataRoute, Unit> {
            requireOperationSucceeded { dataController.clearEntries() }
        }
    }

    get<DataRoute.Compounds, List<CompoundBase>> { params ->
        val pagination = paginationSpecifier(params.from, params.parent.limit)
        val names = dataController.getCompoundNames(pagination)
        names.list
    }
    authenticatedAsAdmin {
        post<DataRoute.Compounds, Map<CompoundName, CompoundInfo>, Unit> { _, insertMap ->
            requireOperationSucceeded {
                dataController.putEntries(Data(compounds = insertMap))
            }
        }
        patch<DataRoute.Compounds, Map<CompoundName, ProxyMap<CompoundInfo>>, Unit> { _, deltaMap ->
            requireOperationSucceeded {
                dataController.updateCompounds(deltaMap)
            }
        }
        delete<DataRoute.Compounds, Unit> { _ ->
            requireOperationSucceeded {
                dataController.removeEntries(cNames = DataController.DELETE_ALL_COMPOUNDS)
            }
        }
    }
    get<DataRoute.Compounds.Name, List<String>> { params ->
        val pagination = paginationSpecifier(params.from, params.parent.parent.limit)
        val names = dataController.getVariantsForCompound(params.compound, pagination)
        names.list
    }
    authenticatedAsAdmin {
        delete<DataRoute.Compounds.Name, Unit> { params ->
            val cn = CompoundName.selectAllVariants(params.compound)
            checkedDelete(cn) {
                dataController.removeEntries(cNames = oneOf(cn))
            }
        }
    }
    get<DataRoute.Compounds.Name.Variant, CompoundInfo> { params ->
        val cn = CompoundName(params.parent.compound, params.variant)
        val data = remapResolutionFailure { dataController.resolveNames(cNames = oneOf(cn)) }
        val info = data.compounds
        check(info.size == 1)
        info.values.first()
    }
    authenticatedAsAdmin {
        put<DataRoute.Compounds.Name.Variant, CompoundInfo, Unit> { params, info ->
            val cn = CompoundName(params.parent.compound, params.variant)
            requireOperationSucceeded {
                dataController.putEntries(Data(compounds = mapOf(cn to info)))
            }
        }
        patch<DataRoute.Compounds.Name.Variant, ProxyMap<CompoundInfo>, Unit> { params, delta ->
            val cn = CompoundName(params.parent.compound, params.variant)
            checkedDelete(cn) {
                dataController.updateCompounds(mapOf(cn to delta))
            }
        }
        delete<DataRoute.Compounds.Name.Variant, Unit> { params ->
            val cn = CompoundName(params.parent.compound, params.variant)
            checkedDelete(cn) {
                dataController.removeEntries(cNames = oneOf(cn))
            }
        }
    }

    get<DataRoute.Blends, List<BlendName>> { params ->
        val pagination = paginationSpecifier(params.from, params.parent.limit)
        val names = dataController.getBlendNames(pagination)
        names.list
    }
    authenticatedAsAdmin {
        post<DataRoute.Blends, Map<BlendName, BlendValue>, Unit> { _, insertMap ->
            requireOperationSucceeded {
                dataController.putEntries(Data(blends = insertMap))
            }
        }
        delete<DataRoute.Blends, Unit> { _ ->
           requireOperationSucceeded {
               dataController.removeEntries(bNames = DataController.DELETE_ALL_BLENDS)
           }
        }
    }
    get<DataRoute.Blends.Name, BlendValue> { params ->
        val data = remapResolutionFailure {
            dataController.resolveNames(
                cNames = DONT_EXPAND_BLEND_COMPOUNDS,
                bNames = listOf(params.blend).assertIsSortedSet()
            )
        }
        val info = data.blends
        check(info.size == 1)
        info.values.first()
    }
    authenticatedAsAdmin {
        put<DataRoute.Blends.Name, BlendValue, Unit> { params, vals ->
            requireOperationSucceeded {
                dataController.putEntries(Data(blends = mapOf(params.blend to vals)))
            }
        }
        delete<DataRoute.Blends.Name, Unit> { params ->
            checkedDelete(params.blend) {
                dataController.removeEntries(bNames = oneOf(params.blend))
            }
        }
    }
    get<DataRoute.Frequencies, List<FrequencyName>> {route ->
        val pagination = paginationSpecifier(route.from, route.parent.limit)
        val names = dataController.getFrequencyNames(pagination)
        names.list
    }
    authenticatedAsAdmin {
        post<DataRoute.Frequencies, Map<FrequencyName, FrequencyValue>, Unit> { _, insertMap ->
            requireOperationSucceeded {
                dataController.putEntries(Data(frequencies = insertMap))
            }
        }
        delete<DataRoute.Frequencies, Unit> { _ ->
            requireOperationSucceeded {
                dataController.removeEntries(fNames = DataController.DELETE_ALL_FREQUENCIES)
            }
        }
    }
    get<DataRoute.Frequencies.Name, FrequencyValue> { params ->
        val data = remapResolutionFailure {
            dataController.resolveNames(fNames = oneOf(params.frequency))
        }
        val info = data.frequencies
        check(info.size == 1)
        info.values.first()
    }
    authenticatedAsAdmin {
        put<DataRoute.Frequencies.Name, FrequencyValue, Unit> { params, vals ->
            requireOperationSucceeded {
                dataController.putEntries(Data(frequencies = mapOf(params.frequency to vals)))
            }
        }
        delete<DataRoute.Frequencies.Name, Unit> { params ->
            checkedDelete(params.frequency) {
                dataController.removeEntries(fNames = oneOf(params.frequency))
            }
        }
    }
    get<DataRoute.Transformers, Map<String, TransformerInfo>> { _ ->
        getTransformersInfo()
    }
}

private inline fun requireOperationSucceeded(block: () -> Boolean) =
    require(remapUOE(block)) {
        "operation failed"
    }

private inline fun checkedDelete(name: Any, block: () -> Boolean) {
    if (!remapUOE(block))
        throw NoSuchElementException("$name not found")
}


// remap IAE when message starts with "unresolved " to NSEE so get desired exception type
@Throws(NoSuchElementException::class)
private inline fun <R> remapResolutionFailure(block: () -> R) =
    try {
        block()
    } catch (e: IllegalArgumentException) {
        if (e.message?.startsWith(DataController.UNRESOLVED) == true) {
            val msg = e.message!!.substring(DataController.UNRESOLVED.length)
            throw NoSuchElementException(msg, e.cause)
        }
        throw e
    }

// Ktor throws a UOE when deserialization fails so we need to remap to a more specific failure to trigger 403 handler
@Throws(AccessException::class)
private inline fun <R> remapUOE(block: () -> R) =
    try {
        block()
    } catch (e: UnsupportedOperationException) {
        throw AccessException(e.message, e.cause)
    }

/** Thrower supplier for saving exception messages. */
private fun throwMissing(cat: String, name: String): () -> Nothing =
    { throw NoSuchElementException("for $cat $name") }

