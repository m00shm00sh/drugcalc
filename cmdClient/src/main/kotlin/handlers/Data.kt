package com.moshy.drugcalc.cmdclient.handlers

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.cmdclient.states.ForData
import com.moshy.drugcalc.cmdclient.states.ForData.*
import com.moshy.drugcalc.cmdclient.states.mapAll
import com.moshy.drugcalc.cmdclient.helpers.*
import com.moshy.drugcalc.cmdclient.io.*
import com.moshy.drugcalc.cmdclient.io.UrlStringSerializer.Companion.encode
import com.moshy.drugcalc.common.toTruthy
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.ProxyMap
import com.moshy.containers.*
import com.moshy.drugcalc.cmdclient.io.data.*
import com.moshy.drugcalc.common.PreferredIODispatcher
import com.moshy.krepl.*
import kotlinx.coroutines.withContext
import kotlin.collections.plus
import kotlin.time.Duration

internal fun AppState.configureData(): Repl.EntryBuilder.() -> Unit = {
    val app = this@configureData
    val forData = app.forData

    val paginator: Paginator = { repl, out, lines -> app.maybePaginate(repl, lines, out) }

    usage = "data"
    help = "enter data editor"
    handler = {
        push("data") onPop@{ _, out ->
            val d = forData.newEntries
            check(forData.currentSection?.editPage.isNullOrEmpty()) {
                "we have an active edit session and missed an exit check"
            }
            if (!d.isNotEmpty())
                return@onPop
            buildList {
                add("uncommitted:")
                if (d.isNotEmpty()) {
                    add("\tnew:")
                    appendDataSummary(d, 2)
                }
            }.let {
                // don't paginate because that's too much interactivity to consider for scope exit
                sendLinesToOutput(it, out)
            }
        }
        clear()

        this["all"] {
            help = "reset current data section"
            handler = {
                it.outputChannel.send("Use quit to leave the current section".asLine())
            }
        }
        this["compounds"] {
            help = "set current data section to compounds"
            handler = {
                require(forData.currentSection == null) {
                    "existing active session detected; please quit it first"
                }
                val compounds = forData.newEntries.compounds
                forData.currentSection = CurrentSection.Compounds(editPage = compounds.toMutableMap())
                forData.newEntries = forData.newEntries.copy(compounds = emptyMap())
                push("compounds", forData.tryLeaveSection())
                registerSectionalHandlersForNew(forData)
            }
        }
        this["blends"] {
            help = "set current data section to blends"
            handler = {
                require(forData.currentSection == null) {
                    "existing active session detected; please quit it first"
                }
                val blends = forData.newEntries.blends
                forData.newEntries = forData.newEntries.copy(blends = emptyMap())
                forData.currentSection = CurrentSection.Blends(editPage = blends.toMutableMap())
                push("blends", forData.tryLeaveSection())
                registerSectionalHandlersForNew(forData)
            }
        }
        this["frequencies"] {
            help = "set current data section to frequencies"
            handler = {
                require(forData.currentSection == null) {
                    "existing active session detected; please quit it first"
                }
                val frequencies = forData.newEntries.frequencies
                forData.newEntries = forData.newEntries.copy(frequencies = emptyMap())
                forData.currentSection = CurrentSection.Frequencies(editPage = frequencies.toMutableMap())
                push("frequencies", forData.tryLeaveSection())
                registerSectionalHandlersForNew(forData)
            }
        }
        this["update-compounds"] {
            require(forData.currentSection == null) {
                "existing active session detected; please quit it first"
            }
            help = "set current data section to compounds updater"
            handler = {
                forData.currentSection = CurrentSection.CompoundsUpdater()
                registerSectionalHandlersForUpdate(forData)
            }
        }

        this["list-local"] {
            help = "list local (uncommitted) entries"
            handler = forData.handleDataListLocal(paginator)
        }
        this["list-all"] {
            help = "list all entries"
            handler = forData.handleDataListAll(paginator)
        }

        this["remove-local"] {
            help = "remove local (uncommitted) entry(es) (semantics depend on current section)"
            usage = """
                $name
                compound-name variant {all=truthy}? |
                blend-name | frequency-name    
            """.trimIndent()
            handler = forData.handleDataRemoveLocal()
        }
        this["remove-remote"] {
            help = "remove remote (server) entry(es) (semantics depend on current section)"
            usage = """
                $name
                compound-name variant {all=truthy}? |
                blend-name | frequency-name    
            """.trimIndent()
            handler = forData.handleDataRemoveRemote(app)
        }

        this["import"] {
            help = "import bulk json"
            usage = "$name filename {over=truthy}? {upd=truthy}?"
            handler = forData.handleImport(paginator)
        }
        this["commit"] {
            help = "upload new and update entries"
            handler = forData.handleSaveRemote(app)
        }
    }
}

private fun ForData.tryLeaveSection(): OnPopHandler = { _, _ ->
    val cur = currentSection
    check(cur != null)
    require(cur.editPage.isEmpty()) {
        "there are unsaved edits to save locally"
    }
    currentSection = null
}

private fun Repl.registerSectionalHandlers(forData: ForData) {
    this["detail"] {
        help = "list detail for entry (semantics depend on current section)"
        usage = "detail ([variant] | compound-name [variant] | blend-name | frequency-name)"
        handler = forData.handleDataListDetail()
    }
}
private fun Repl.registerSectionalHandlersForNew(forData: ForData) {
    registerSectionalHandlers(forData)
    this["new"] {
        help = "add new entry (semantics depend on current section)"
        val uCompoundKw = "{halfLife|pctActive|note}*"
        usage = """
                $name
                compound-name variant $uCompoundKw
                blend-name {.note=note} {blend-component=dose}+ 
                frequency-name frequency-value+
            """.trimIndent()
        handler = forData.handleDataNew()
    }

    this["save"] {
        help = "save pending edits to local data"
        handler = forData.handleSaveLocal()
    }
}
private fun Repl.registerSectionalHandlersForUpdate(forData: ForData) {
    registerSectionalHandlers(forData)
    this["update"] {
        help = "add update entry (semantics depend on current section)"
        val uCompoundKw = "{halfLife|pctActive|note}*"
        usage = "$name compound-name variant $uCompoundKw"
        handler = forData.handleDataUpdate()
    }
}

private fun ForData.handleDataListDetail() = discardRepl { (pos, _, _, out) ->
    val name = currentSection?.produceName(pos)
        ?: throw IllegalArgumentException("could not produce name")
    val entry = when (val entry = currentSection) {
        is CurrentSection.Compounds ->
            fetchCompoundValue(name as CompoundName)
        is CurrentSection.Blends ->
            fetchBlendValue(name as BlendName)
        is CurrentSection.Frequencies ->
            fetchFrequencyValue(name as FrequencyName)
        is CurrentSection.CompoundsUpdater ->
            entry.editPage[name as CompoundName]
                ?: throw NoSuchElementException("for $name")
        null ->
            error("current data section is required")
        }
    out.send(entry.toString().asLine())
}

private fun ForData.handleDataListLocal(paginate: Paginator) = withRepl { repl, (_, _, _, out) ->
    buildList {
        when (val current = currentSection) {
            is CurrentSection.Compounds,
            is CurrentSection.CompoundsUpdater -> {
                // kotlin can't deduce that current.editPage is
                // Map<CompoundName, CompoundInfo | ProxyMap<CompoundInfo>>
                @Suppress("UNCHECKED_CAST")
                val keys = current.editPage.keys as Set<CompoundName>
                appendCompoundsMap(keys.reshapeByBaseAndVariant())
            }
            is CurrentSection.Blends,
            is CurrentSection.Frequencies ->
                appendSet(current.editPage.keys)
            null ->
                appendData(newEntries)
        }
        if (isEmpty())
            add("")
    }.let {
        paginate(repl, out, it)
    }
}

private fun ForData.handleDataListAll(paginate: Paginator) = withRepl { repl, (_, _, _, out) ->
    buildList {
        when (currentSection) {
            is CurrentSection.Compounds -> {
                val names =
                    fetchCompoundBaseNames()
                    .associateWith { fetchCompoundVariantNames(it) }
                names.forEach { (k, v) ->
                    appendCompoundVariants(k, v)
                }
            }

            is CurrentSection.Blends -> {
                val names = fetchBlendNames()
                appendLass(names)
            }

            is CurrentSection.Frequencies -> {
                val names = fetchFrequencyNames()
                appendLass(names)
            }

            is CurrentSection.CompoundsUpdater ->
                throw IllegalArgumentException("list-all unsupported in update mode")

            null -> {
                appendData(newEntries)
            }
        }
    }.let {
        paginate(repl, out, it)
    }
}

private fun ForData.handleDataNew() = discardRepl { (pos, kw, _, out) ->
    val name = currentSection?.produceName(pos)
        ?: throw IllegalArgumentException("could not produce name")
    when (val current = currentSection) {
        is CurrentSection.Compounds -> {
            name as CompoundName
            val remap = kw.mapKeys { (k, _) ->
                requireNotNull(compoundInfoRemap[k]) {
                    "unrecognized kw: $k"
                }
            }
            val cInfo = remap.toCompoundUpdater().createObject()
            current.editPage[name] = cInfo
            out.send("new compound $name".asLine())
        }

        is CurrentSection.Blends -> {
            name as BlendName
            val note = kw[".note"] ?: kw[".n"] ?: ""
            val components = kw.keys.filter { !it.startsWith(".") }
                .associate { k ->
                    val compound = CNSerializer.fromString(k)
                    val dose = kw[k]!!.toDouble()
                    compound to dose
                }
            val bv = BlendValue(components, note)
            current.editPage[name] = bv
            out.send("new blend ${quote(name)}".asLine())
        }

        is CurrentSection.Frequencies -> {
            name as FrequencyName
            require(pos.size >= 2) {
                "exactly one name argument and one or more value arguments required"
            }
            val values = pos.subListFrom(1).map(Duration::parse)
            val fv = FrequencyValue(values)
            current.editPage[name] = fv
            out.send("new frequency ${quote(name)}".asLine())
        }

        is CurrentSection.CompoundsUpdater,
        null ->
            error("no valid active page")
    }
}

private fun ForData.handleDataUpdate() = discardRepl { (pos, kw, _, out) ->
    val name = currentSection?.produceName(pos)
        ?: throw IllegalArgumentException("could not produce name")
    when (val current = currentSection) {
        is CurrentSection.CompoundsUpdater -> {
            name as CompoundName
            require(newEntries.compounds[name] == null) {
                "conflict: local data contains $name"
            }
            val remap = kw.mapKeys { (k, _) ->
                requireNotNull(compoundInfoRemap[k]) {
                    "unrecognized kw: $k"
                }
            }
            val cUpdater = remap.toCompoundUpdater()
            current.editPage[name] = cUpdater
            out.send("update compound $name".asLine())
        }
        is CurrentSection.Compounds,
        is CurrentSection.Blends,
        is CurrentSection.Frequencies,
        null ->
            error("no valid active update page")
    }
}

private fun ForData.handleDataRemoveLocal() = discardRepl { (pos, kw, in_, out) ->
    val current = currentSection
    val name = try {
        current?.produceName(pos)
    } catch (_: IllegalArgumentException) {
        null
    }
    val all = kw["all"].toTruthy()

    var result: String

    // we can filter by compound base if we're in *some* compounds page and have the correct inputs
    if ((current is CurrentSection.Compounds || current is CurrentSection.CompoundsUpdater)
        && name != null && all
    ) {
        name as CompoundName
        @Suppress("UNCHECKED_CAST")
        val cKeys = current.editPage.keys as Set<CompoundName>
        cKeys.filter { it.compound == name.compound }
            .let { ks ->
                // `ks.any { remove() != null }` short circuits so map-reduce it instead
                val didRemove = ks.map { current.editPage.remove(it) != null }
                    .reduce { a, x -> a || x }
                if (didRemove)
                    result = "compound ${quote(name.compound)} <all-variants>"
                else
                    throw NoSuchElementException("no such compound $name")
            }
    } else {
        name?.let { _ ->
            current?.let { _ ->
                current.editPage.remove(name)
                    ?.let { result = "$name" }
                    ?: throw NoSuchElementException("no such key $name")
            } ?: throw IllegalArgumentException("expected zero arguments")
        } ?: let { _ ->
            current?.let { _ ->
                confirmOperation("delete all ${current.name}", in_, out)
                current.editPage.clear()
                result = "<all ${current.name}>"
            } ?: let { _ ->
                confirmOperation("all data", in_, out)
                newEntries = Data()
                result = "<all data>"
            }
        }
    }
    out.send("remove-local: $result".asLine())
}

private fun ForData.handleDataRemoveRemote(app: AppState) = discardRepl { (pos, kw, in_, out) ->
    val current = currentSection
    val name = try {
        current?.produceName(pos)
    } catch (_: IllegalArgumentException) {
        null
    }
    val all = kw["all"].toTruthy()

    var url: String
    var result: String

    when (current) {
        is CurrentSection.Compounds -> {
            if (name != null) {
                name as CompoundName
                val cbv = name.compound.value.encode()
                val cvv = name.variant.encode().ifBlank { "-" }
                if (!all) {
                    url = "/api/compounds/$cbv/$cvv"
                    result = "compound $name"
                } else {
                    url = "/api/compounds/$cbv"
                    result = "compound ${quote(name.compound)} <all-variants>"
                }
            } else {
                confirmOperation("delete all compounds", in_, out)
                url = "/api/data/compounds"
                result = "<all compounds>"
            }
        }

        is CurrentSection.Blends -> {
            if (name != null) {
                name as BlendName
                url = "/api/blends/${name.value.encode()}"
                result = "blend $name"
            } else {
                confirmOperation("delete all blends", in_, out)
                url = "/api/blends"
                result = "<all blends>"
            }
        }

        is CurrentSection.Frequencies -> {
            if (name != null) {
                name as FrequencyName
                url = "/api/frequencies/${name.value.encode()}"
                result = "frequency $name"
            } else {
                confirmOperation("delete all frequencies", in_, out)
                url = "/api/frequencies"
                result = "<all frequencies>"
            }
        }

        // we can't throw an ISE here because making the state unreachable would complicate the
        // stateful handlers too much since we're weaving inclusions and command exclusions
        is CurrentSection.CompoundsUpdater ->
            throw IllegalArgumentException("no updaters to remove")

        null -> {
            confirmOperation("delete all entries", in_, out)
            url = "/api/data"
            result = "<all data>"
        }
    }

    check(url.isNotEmpty()) {
        "unset url"
    }
    check(result.isNotEmpty()) {
        "unset result"
    }
    app.doRequest(NetRequestMethod.Delete, url, flags = FLAGS_AUTH)
    out.send("remove-remote: $result".asLine())
}

private fun ForData.handleImport(paginate: Paginator) = withRepl { repl, (pos, kw, in_, out) ->
    val file = pos.firstOrNull() ?: throw IllegalArgumentException("missing file")
    val current = currentSection
    if (current != null)
        confirmOperation("import only (sub)section", in_, out)
    val allowOverwrite = kw["over"].toTruthy()
    val loadedSection = when (current) {
        is CurrentSection.Compounds ->
            io(::loadCompounds)(file)
        is CurrentSection.Blends ->
            io(::loadBlends)(file)
        is CurrentSection.Frequencies ->
            io(::loadFrequencies)(file)
        is CurrentSection.CompoundsUpdater ->
            io(::loadCompoundsUpdater)(file)
        null ->
            io(::loadData)(file)
    }

    buildList {
        add("import:")
        @Suppress("UNCHECKED_CAST")
        when (current) {
            is CurrentSection.Compounds,
            is CurrentSection.Blends,
            is CurrentSection.Frequencies -> {
                val m = current.editPage as MutableMap<Comparable<*>, *>
                loadedSection as Map<Comparable<*>, *>
                checkImport(m, current.name, toLoad = loadedSection, allowOverwrite = allowOverwrite)
                val keys = loadedSection.keys
                if (current is CurrentSection.Compounds) {
                    keys as Set<CompoundName>
                    appendCompoundsMap(keys.reshapeByBaseAndVariant(), "compounds")
                } else {
                    appendSet(keys, current.name)
                }
            }

            is CurrentSection.CompoundsUpdater -> {
                loadedSection as Map<CompoundName, ProxyMap<CompoundInfo>>
                checkImport(
                    current.editPage, current.name,
                    newEntries.compounds.keys,
                    loadedSection, allowOverwrite
                )
                val keys = loadedSection.keys
                appendCompoundsMap(keys.reshapeByBaseAndVariant(), current.name)
            }

            null -> {
                loadedSection as Data
                val compounds = checkImport(
                    newEntries.compounds, "compounds",
                    toLoad = loadedSection.compounds, allowOverwrite = allowOverwrite
                )
                val blends = checkImport(
                    newEntries.blends, "blends",
                    toLoad = loadedSection.blends, allowOverwrite = allowOverwrite
                )
                val frequencies = checkImport(
                    newEntries.frequencies, "frequencies",
                    toLoad = loadedSection.frequencies, allowOverwrite = allowOverwrite
                )
                newEntries = Data(compounds, blends, frequencies)
                appendData(loadedSection)
            }
        }
    }.let {
        paginate(repl, out, it)
    }
}

private fun <K, V> checkImport(
    existing: Map<K, V>,
    name: String,
    otherConflictKeys: Set<K> = emptySet(),
    toLoad: Map<K, V>,
    allowOverwrite: Boolean
): Map<K, V> =
    checkImport(existing, name, otherConflictKeys, toLoad, allowOverwrite) { a, b ->
        a + b
    }
// ugly and type-unsafe to use the base class for the generic params but there's no cleaner solution
@JvmName($$"checkImport$Mutable")
private fun checkImport(
    existing: MutableMap<Comparable<*>, *>,
    name: String,
    otherConflictKeys: Set<Comparable<*>> = emptySet(),
    toLoad: Map<Comparable<*>, *>,
    allowOverwrite: Boolean
): Map<Comparable<*>, *> =
    checkImport(existing, name, otherConflictKeys, toLoad, allowOverwrite) { a, b ->
        a as MutableMap
        a.putAll(b)
        a
    }
private fun <K, V> checkImport(
    existing: Map<K, V>,
    name: String,
    otherConflictKeys: Set<K> = emptySet(),
    toLoad: Map<K, V>,
    allowOverwrite: Boolean,
    onSuccess: (Map<K, V>, Map<K, V>) -> Map<K, V>
): Map<K, V> {
    if (!allowOverwrite) {
        val conflict = existing.keys.intersect(toLoad.keys)
        require(conflict.isEmpty()) {
            "$name: conflict(s): $conflict"
        }
    }
    val otherConflict = otherConflictKeys.intersect(toLoad.keys)
    require(otherConflict.isEmpty()) {
        "$name: other conflict(s): $otherConflict"
    }

    return onSuccess(existing, toLoad)
}

private fun ForData.handleSaveLocal() = discardRepl { (_, _, _, out) ->
    if (currentSection?.editPage.isNullOrEmpty())
        return@discardRepl
    when (val current = currentSection) {
        is CurrentSection.Compounds ->
            newEntries = newEntries.copy(compounds = current.editPage)
        is CurrentSection.Blends ->
            newEntries = newEntries.copy(blends = current.editPage)
        is CurrentSection.Frequencies ->
            newEntries = newEntries.copy(frequencies = current.editPage)
        is CurrentSection.CompoundsUpdater ->
            error("save-local enabled for page=update-compounds")
        null ->
            error("save-local enabled for page=null")
    }
    currentSection?.editPage?.clear()
}

private fun ForData.handleSaveRemote(app: AppState) = discardRepl { (_, _, _, out) ->
    var result: String
    when (val current = currentSection) {
        // similar situation as with handleRemoveRemote
        is CurrentSection.Compounds,
        is CurrentSection.Blends,
        is CurrentSection.Frequencies ->
            throw IllegalArgumentException("incompatible edit mode; please save and exit")

        is CurrentSection.CompoundsUpdater -> {
            require(current.editPage.isNotEmpty()) {
                "nothing to commit"
            }
            app.doRequest<Unit, Map<CompoundName, ProxyMap<CompoundInfo>>>(
                NetRequestMethod.Patch, "/api/data/compounds",
                flags = NRF_AUTH_AND_JSON,
                body = current.editPage
            )
            current.editPage.clear()
            result = "updates"
        }

        null -> {
            require(newEntries.isNotEmpty()) {
                "nothing to commit"
            }
            result = newEntries.pushRemote(app)
            newEntries = Data()
        }
    }

    check(result.isNotEmpty()) {
        "unset result"
    }
    out.send("commit: $result".asLine())
}

private suspend fun Data.pushRemote(app: AppState): String {
    val hasMultipleDataSections = mapAll { if (it.isNotEmpty()) 1 else 0 }.sum() > 1
    val compoundsBV = compounds.reshapeByBaseAndVariant()
    if (hasMultipleDataSections) {
        app.doRequest<Unit, Data>(
            NetRequestMethod.Post, "/api/data/compounds",
            flags = NRF_AUTH_AND_JSON,
            body = this
        )
        return "new data"
    }

    val doMultipleCompounds = compoundsBV.keys.size > 1

    return when {
        compounds.isNotEmpty() && !doMultipleCompounds -> {
            val cb = compounds.keys.first().compound
            val cbv = cb.value.encode()

            val body = compoundsBV.values.first()
            check(body.isNotEmpty()) {
                "BV transformation produced an empty map"
            }

            app.doRequest<Unit, Map<String, CompoundInfo>>(
                NetRequestMethod.Post, "/api/data/compounds/$cbv",
                flags = NRF_AUTH_AND_JSON,
                body = body,
            )
            "compound; new variant(s)"
        }

        compounds.isNotEmpty() && doMultipleCompounds -> {
            app.doRequest<Unit, Map<CompoundName, CompoundInfo>>(
                NetRequestMethod.Post, "/api/data/compounds",
                flags = NRF_AUTH_AND_JSON,
                body = compounds,
            )
            "new compounds"
        }

        blends.isNotEmpty() -> {
            app.doRequest<Unit, Map<BlendName, BlendValue>>(
                NetRequestMethod.Post, "/api/data/blends",
                flags = NRF_AUTH_AND_JSON,
                body = blends,
            )
            "new blend(s)"
        }

        frequencies.isNotEmpty() -> {
            app.doRequest<Unit, Map<FrequencyName, FrequencyValue>>(
                NetRequestMethod.Post, "/api/data/frequencies",
                flags = NRF_AUTH_AND_JSON,
                body = frequencies,
            )
            "new frequency(ies)"
        }

        else ->
            error("empty Data got through multiple checks")
    }
}

private fun Set<CompoundName>.reshapeByBaseAndVariant(): Map<CompoundBase, Set<String>> =
    buildMap<CompoundBase, MutableSet<String>> {
        this@reshapeByBaseAndVariant.forEach { k ->
            require(!k.selectAllVariants) {
                "selectAllVariants only allowed in deletion expressions"
            }
            getOrPut(k.compound) { mutableSetOf() }.add(k.variant)
        }
    }
private fun <V> Map<CompoundName, V>.reshapeByBaseAndVariant(): Map<CompoundBase, Map<String, V>> =
    buildMap<CompoundBase, MutableMap<String, V>> {
        this@reshapeByBaseAndVariant.forEach { (k, v) ->
            require(!k.selectAllVariants) {
                "selectAllVariants only allowed in deletion expressions"
            }
            getOrPut(k.compound) { mutableMapOf() }[k.variant] = v
        }
    }

private fun LinesBuilder.appendData(d: Data, indent: Int = 0) {
    appendCompoundsMap(d.compounds.keys.reshapeByBaseAndVariant(), "compounds", indent)
    appendSet(d.blends.keys, "blends", indent)
    appendSet(d.frequencies.keys, "frequencies", indent)
}

private fun LinesBuilder.appendDataSummary(d: Data, indent: Int = 0) {
    val tabs = "\t".repeat(indent)
    val entries = listOf(
        "compound entries" to d.compounds.size,
        "blends" to d.blends.size,
        "frequencies" to d.frequencies.size
    )
    entries.forEach { (name, n) ->
        if (n > 0)
            add("${tabs}${name}: $n")
    }
}

private fun LinesBuilder.appendCompoundsMap(m: Map<CompoundBase, Set<String>>, name: String = "", indent: Int = 0) {
    val tabs = "\t".repeat(indent)
    if (m.isEmpty())
        return
    if (name.isNotEmpty())
        add("$tabs$name")
    m.forEach { (base, variants) ->
        appendCompoundVariants(base, variants, indent + 1)
    }
}

private fun LinesBuilder.appendCompoundVariants(k: CompoundBase, v: Collection<String>, indent: Int = 1) {
    buildString {
        append(quote(k.value))
        if (v.isNotEmpty() && v != listOf(""))
            append(": ")
        val sortedV = (v as? ListAsSortedSet<String>) ?: v.sorted()
        sortedV.joinTo(this, " ") { quote(it) }
    }.let {
        val tabs = "\t".repeat(indent)
        add("$tabs$it")
    }
}

private fun <K> LinesBuilder.appendSet(s: Set<K>, name: String = "", indent: Int = 0) {
    val tabs = "\t".repeat(indent)
    if (s.isEmpty())
        return
    if (name.isNotEmpty())
        add("$tabs$name")
    val indentItem = indent + (if (name.isNotEmpty()) 1 else 0)
    val tabsNext = "\t".repeat(indentItem)
    s.forEach {
        add("$tabsNext$it")
    }
}

private fun <T: Comparable<T>> LinesBuilder.appendLass(lass: ListAsSortedSet<T>, indent: Int = 0) {
    val tabs = "\t".repeat(indent)
    lass.forEach {
        add("$tabs$it")
    }
}

private val compoundInfoRemap = mapOf(
    id(name(CompoundInfo::halfLife)),
    aliasName("halflife", CompoundInfo::halfLife),
    aliasName("hl", CompoundInfo::halfLife),
    id(name(CompoundInfo::pctActive)),
    aliasName("pctactive", CompoundInfo::pctActive),
    aliasName("ac", CompoundInfo::pctActive),
    id(name(CompoundInfo::note)),
    aliasName("n", CompoundInfo::note),
)

private fun Map<String, String>.toCompoundUpdater(): ProxyMap<CompoundInfo> =
    this.let { m ->
        buildMap {
            m["halfLife"]?.let { put("halfLife", Duration.parse(it)) }
            m["pctActive"]?.let { put("pctActive", it.toDouble()) }
            m["note"]?.let { put("note", it) }
        }.let { ProxyMap<CompoundInfo>(it) }
    }

private val FLAGS_AUTH = NetRequestFlags.of(NetRequestFlag.AUTH_JWT)

private fun <R> io(func: (String) -> R): suspend (String) -> R = {
    withContext(PreferredIODispatcher()) {
        func(it)
    }
}