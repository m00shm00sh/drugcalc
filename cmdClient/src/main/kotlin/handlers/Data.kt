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
import com.moshy.krepl.*
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
            val u = forData.updateEntries
            if (!d.isNotEmpty() && !u.isNotEmpty())
                return@onPop
            buildList {
                add("uncommitted:")
                if (d.isNotEmpty()) {
                    add("\tnew:")
                    appendDataSummary(d, 2)
                }
                if (u.isNotEmpty()) {
                    add("\tupdate:")
                    appendDataUpdaterSummary(u, 2)
                }
            }.let {
                // don't paginate because that's too much interactivity to consider for scope exit
                sendLinesToOutput(it, out)
            }
        }
        clear()
        val renamer = renamer()

        this["all"] {
            help = "reset current data section"
            handler = {
                forData.currentSection = null
                renamer.set("data")
            }
        }
        this["compounds"] {
            help = "set current data section to compounds"
            handler = {
                forData.currentSection = CurrentSection.Compounds()
                renamer.set("data:compounds")
            }
        }
        this["compound"] {
            help = "set active compound"
            usage = "compound compound-name"
            handler = { (pos, _, _, _) ->
                require(pos.size == 1) {
                    "expected one argument"
                }
                val name = pos.first()
                forData.currentSection = CurrentSection.Compounds(CompoundBase(name))
                renamer.set("data:compounds:${quote(name)}")
            }
        }
        this["blends"] {
            help = "set current data section to blends"
            handler = {
                forData.currentSection = CurrentSection.Blends
                renamer.set("data:blends")
            }
        }
        this["frequencies"] {
            help = "set current data section to frequencies"
            handler = {
                forData.currentSection = CurrentSection.Frequencies
                renamer.set("data:frequencies")
            }
        }
        this["show-section"] {
            help = "show current data section"
            handler = { (_, _, _, o) ->
                with(forData) {
                    when (val current = currentSection) {
                        is CurrentSection.Compounds if current.compound != null ->
                            "compound ${quote(current.compound.value)}"

                        is CurrentSection.Compounds ->
                            "compounds"

                        CurrentSection.Blends -> "blends"
                        CurrentSection.Frequencies -> "frequencies"
                        null -> "all"
                    }
                }.let {
                    o.send(it.asLine())
                }
            }
        }
        this["section"] = this["show-section"]

        this["detail"] {
            help = "list detail for entry (semantics depend on current section)"
            usage = "detail ([variant] | compound-name [variant] | blend-name | frequency-name)"
            handler = forData.handleDataListDetail()
        }

        this["list-local"] {
            help = "list local (uncommitted) entries"
            handler = forData.handleDataListLocal(paginator)
        }
        this["list-remote"] {
            help = "list remote (server) entries"
            handler = forData.handleDataListRemote(paginator)
        }
        this["list-all"] {
            help = "list all (server+uncommitted) entries"
            handler = forData.handleDataListAll(paginator)
        }

        this["new"] {
            help = "add new entry (semantics depend on current section)"
            val uCompoundKw = "{halfLife|pctActive|note}*"
            usage = """
                $name
                variant $uCompoundKw |
                compound-name variant $uCompoundKw
                blend-name {.note=note} {blend-component=dose}+ 
                frequency-name frequency-value+
            """.trimIndent()
            handler = forData.handleDataNew()

        }
        this["update"] {
            help = "add update entry (semantics depend on current section)"
            val uCompoundKw = "{halfLife|pctActive|note}*"
            usage = "$name (variant $uCompoundKw | compound-name variant $uCompoundKw)"
            handler = forData.handleDataUpdate()

        }

        this["evict-cache"] {
            help = "clear remote cache"
            handler = {
                with(forData) {
                    allNameCaches.forEach { it.invalidateAll() }
                    allValueCaches.forEach { it.invalidateAll() }
                }
            }
        }

        this["undo"] {
            help = "undo latest add or update"
            handler = forData.handleDataUndo()

        }
        this["remove-local"] {
            help = "remove local (uncommitted) entry(es) (semantics depend on current section)"
            usage = """
                $name
                variant {all=truthy}? | compound-name variant {all=truthy}? |
                blend-name | frequency-name    
            """.trimIndent()
            handler = forData.handleDataRemoveLocal()
        }
        this["remove-remote"] {
            help = "remove remote (server) entry(es) (semantics depend on current section)"
            usage = """
                $name
                variant {all=truthy}? | compound-name variant {all=truthy}? |
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
            handler = forData.handleCommit(app)
        }
    }
}


private fun ForData.handleDataListDetail() = discardRepl { (pos, _, _, out) ->
    when (val current = currentSection) {
        is CurrentSection.Compounds -> {
            val base = current.compound
                ?: pos.firstOrNull()?.let(::CompoundBase)
                ?: throw IllegalArgumentException("expected compound name")
            val variant = run {
                val idx = if (base == current.compound) 0 else 1
                pos.getOrNull(idx) ?: ""
            }
            val cn = CompoundName(base, variant)
            val updEntry = updateEntries.compounds[cn]
            val newEntry = newEntries.compounds[cn]
            when {
                updEntry != null -> {
                    val remoteEntry = compoundValuesCache.get(cn)
                    updEntry.applyToObject(remoteEntry)
                }

                newEntry != null -> newEntry
                else -> compoundValuesCache.get(cn)
            }
        }

        CurrentSection.Blends -> {
            val e = lastEntry as? LastEntry.Blend
            val what = e?.name
                ?: pos.firstOrNull()?.let(::BlendName)
                ?: throw IllegalArgumentException("expected blend name")
            val newEntry = newEntries.blends[what]
            when {
                newEntry != null -> newEntry
                else -> blendValuesCache.get(what)
            }
        }

        CurrentSection.Frequencies -> {
            val e = lastEntry as? LastEntry.Frequency
            val what = e?.name
                ?: pos.firstOrNull()?.let(::FrequencyName)
                ?: throw IllegalArgumentException("expected frequency name")
            val newEntry = newEntries.frequencies[what]
            when {
                newEntry != null -> newEntry
                else -> frequencyValuesCache.get(what)
            }
        }

        null -> {
            throw IllegalArgumentException("current data section is required")
        }
    }.let {
        out.send(it.toString().asLine())
    }
}

private fun ForData.handleDataListLocal(paginate: Paginator) = withRepl { repl, (_, _, _, out) ->
    buildList {
        if (newEntries.isNotEmpty()) {
            add("new:")
            when (val current = currentSection) {
                is CurrentSection.Compounds ->
                    appendData(Data(compounds = newEntries.compounds), compoundFilter = current.compound, indent = 1)

                CurrentSection.Blends ->
                    appendData(Data(blends = newEntries.blends), indent = 1)

                CurrentSection.Frequencies ->
                    appendData(Data(frequencies = newEntries.frequencies), indent = 1)

                null ->
                    appendData(newEntries, indent = 1)
            }
        }
        if (updateEntries.isNotEmpty()) {
            add("update:")
            when (val current = currentSection) {
                is CurrentSection.Compounds ->
                    appendDataUpdater(updateEntries, compoundFilter = current.compound, indent = 1)

                CurrentSection.Blends,
                CurrentSection.Frequencies -> { /* noop */
                }

                null ->
                    appendDataUpdater(updateEntries, indent = 1)
            }
        }
        if (isEmpty())
            add("")
    }.let {
        paginate(repl, out, it)
    }
}

private fun ForData.handleDataListRemote(paginate: Paginator) = withRepl { repl, (_, _, _, out) ->
    buildList {
        when (val current = currentSection) {
            is CurrentSection.Compounds if current.compound != null -> {
                val vs = compoundVariantsCache.get(current.compound)
                appendCompoundVariants(current.compound, vs)
            }

            is CurrentSection.Compounds -> {
                val kvs = compoundBasesCache.get().associateWith { compoundVariantsCache.get(it) }
                kvs.forEach { (k, v) ->
                    appendCompoundVariants(k, v)
                }
            }

            is CurrentSection.Blends -> {
                val bvs = blendNamesCache.get()
                appendLass(bvs)
            }

            is CurrentSection.Frequencies -> {
                val fvs = frequencyNamesCache.get()
                appendLass(fvs)
            }

            null -> {
                val remoteCompounds = compoundBasesCache.get().associateWith { compoundVariantsCache.get(it) }
                val remoteBlends = blendNamesCache.get()
                val remoteFrequencies = frequencyNamesCache.get()
                if (remoteCompounds.isNotEmpty()) {
                    add("compounds:")
                    remoteCompounds.forEach { (k, v) ->
                        appendCompoundVariants(k, v)
                    }
                }
                if (remoteBlends.isNotEmpty()) {
                    add("blends:")
                    appendLass(remoteBlends)
                }
                if (remoteFrequencies.isNotEmpty()) {
                    add("frequencies:")
                    appendLass(remoteFrequencies)
                }
            }
        }
    }.let {
        paginate(repl, out, it)
    }
}

private fun ForData.handleDataListAll(paginate: Paginator) = withRepl { repl, (_, _, _, out) ->
    buildList {
        when (val current = currentSection) {
            is CurrentSection.Compounds if current.compound != null -> {
                val localCompounds = newEntries.compounds.reshapeByBaseAndVariant()
                val vs = (try {
                    compoundVariantsCache.get(current.compound)
                } catch (_: NoSuchElementException) {
                    emptyList<String>().assertIsSortedSet()
                }).buildCopy {
                    val localVariants = localCompounds[current.compound]?.keys ?: emptyList()
                    addAll(localVariants)
                }
                appendCompoundVariants(current.compound, vs)
            }

            is CurrentSection.Compounds -> {
                val localCompounds = newEntries.compounds.reshapeByBaseAndVariant()
                val kvs = buildMap {
                    compoundBasesCache.get().forEach { cBase ->
                        val remoteVariants = compoundVariantsCache.get(cBase)
                        val localVariants = localCompounds[cBase]?.keys ?: emptyList()
                        this[cBase] = remoteVariants.buildCopy { addAll(localVariants) }
                    }
                    localCompounds.forEach { (base, variants) ->
                        getOrPut(base) { variants.keys.copyToSortedSet() }
                    }
                }
                kvs.forEach { (k, v) ->
                    appendCompoundVariants(k, v)
                }
            }

            is CurrentSection.Blends -> {
                val bvs = blendNamesCache.get().buildCopy { addAll(newEntries.blends.keys) }
                appendLass(bvs)
            }

            is CurrentSection.Frequencies -> {
                val fvs = frequencyNamesCache.get().buildCopy { addAll(newEntries.frequencies.keys) }
                appendLass(fvs)
            }

            null -> {
                val localCompounds = newEntries.compounds.reshapeByBaseAndVariant()
                val allCompounds = buildMap {
                    compoundBasesCache.get().forEach { cBase ->
                        val remoteVariants = compoundVariantsCache.get(cBase)
                        val localVariants = localCompounds[cBase]?.keys ?: emptyList()
                        this[cBase] = remoteVariants.buildCopy { addAll(localVariants) }
                    }
                    localCompounds.forEach { (base, variants) ->
                        getOrPut(base) { variants.keys.copyToSortedSet() }
                    }
                }
                val allBlends = blendNamesCache.get().buildCopy { addAll(newEntries.blends.keys) }
                val allFrequencies = frequencyNamesCache.get()
                    .buildCopy { addAll(newEntries.frequencies.keys) }

                if (allCompounds.isNotEmpty()) {
                    add("compounds:")
                    allCompounds.forEach { (k, v) ->
                        appendCompoundVariants(k, v)
                    }
                }
                if (allBlends.isNotEmpty()) {
                    add("blends:")
                    appendLass(allBlends)
                }
                if (allFrequencies.isNotEmpty()) {
                    add("frequencies:")
                    appendLass(allFrequencies)
                }
            }
        }
        if (updateEntries.isNotEmpty()) {
            "update:"
            when (val current = currentSection) {
                is CurrentSection.Compounds ->
                    appendDataUpdater(updateEntries, compoundFilter = current.compound)

                CurrentSection.Blends,
                CurrentSection.Frequencies -> {
                }

                null ->
                    appendDataUpdater(updateEntries)
            }
        }
    }.let {
        paginate(repl, out, it)
    }
}

private fun ForData.handleDataNew() = discardRepl { (pos, kw, _, out) ->
    clearEditState()
    when (val current = currentSection) {
        is CurrentSection.Compounds -> {
            require(pos.size in 0..2) {
                "at most two name arguments required"
            }
            val name = pos.firstOrNull()
            val remap = kw.mapKeys { (k, _) ->
                requireNotNull(compoundInfoRemap[k]) {
                    "unrecognized kw: $k"
                }
            }

            val base = current.compound
                ?: name?.let(::CompoundBase)
                ?: throw IllegalArgumentException("expected compound base")
            val variant = run {
                val idx = if (base == current.compound) 0 else 1
                pos.getOrNull(idx) ?: ""
            }
            val cn = CompoundName(base, variant)
            val entry = mapOf(cn to remap.toCompoundUpdater().createObject())
            require(cn !in updateEntries.compounds) {
                "there is an uncommitted update entry for compound $cn; aborting"
            }
            newEntries = newEntries.copy(compounds = newEntries.compounds + entry)
            lastEntry = LastEntry.Compound(cn)
            lastEdit = LastEdit.NEW
            out.send("new compound $cn".asLine())
        }

        CurrentSection.Blends -> {
            require(pos.size == 1) {
                "exactly one name argument required"
            }
            val name = pos.first()
            val note = kw[".note"] ?: kw[".n"] ?: ""
            val components = kw.keys.filter { !it.startsWith(".") }
                .associate { k ->
                    val compound = CNSerializer.fromString(k)
                    val dose = kw[k]!!.toDouble()
                    compound to dose
                }
            val bn = BlendName(name)
            val bv = mapOf(bn to BlendValue(components, note))
            newEntries = newEntries.copy(blends = newEntries.blends + bv)
            lastEntry = LastEntry.Blend(bn)
            lastEdit = LastEdit.NEW
            out.send("new blend ${quote(bn)}".asLine())
        }

        CurrentSection.Frequencies -> {
            require(pos.size >= 2) {
                "exactly one name argument and one or more value arguments required"
            }
            val name = pos.first()
            val values = pos.subListFrom(1).map(Duration::parse)
            val fn = FrequencyName(name)
            val fv = mapOf(fn to FrequencyValue(values))
            newEntries = newEntries.copy(frequencies = newEntries.frequencies + fv)
            lastEntry = LastEntry.Frequency(fn)
            lastEdit = LastEdit.NEW
            out.send("new frequency ${quote(fn)}".asLine())
        }

        null ->
            throw IllegalArgumentException("no active page")
    }
}

private fun ForData.handleDataUpdate() = discardRepl { (pos, kw, _, out) ->
    clearEditState()
    when (val current = currentSection) {
        is CurrentSection.Compounds -> {
            require(pos.size in 0..2) {
                "at most two name arguments required"
            }
            val name = pos.firstOrNull()
            val remap = kw.mapKeys { (k, _) ->
                requireNotNull(compoundInfoRemap[k]) {
                    "unrecognized kw: $k"
                }
            }

            val base = current.compound
                ?: name?.let(::CompoundBase)
                ?: throw IllegalArgumentException("expected compound base")
            val variant = run {
                val idx = if (base == current.compound) 0 else 1
                pos.getOrNull(idx) ?: ""
            }
            val cn = CompoundName(base, variant)
            val entry = mapOf(cn to remap.toCompoundUpdater())
            require(cn !in newEntries.compounds) {
                "there is an uncommitted new entry for compound $cn; aborting"
            }
            updateEntries = updateEntries.copy(compounds = updateEntries.compounds + entry)
            lastEntry = LastEntry.Compound(cn)
            lastEdit = LastEdit.UPDATE
            out.send("update compound: $cn".asLine())
        }

        CurrentSection.Blends,
        CurrentSection.Frequencies,
        null ->
            throw IllegalArgumentException("current data section must be compounds or compound")
    }
}

private fun ForData.handleDataUndo() = discardRepl { (_, _, _, out) ->
    when (lastEdit) {
        LastEdit.NEW -> handleDataUndoNew(out)
        LastEdit.UPDATE -> handleDataUndoUpdate(out)
        null -> throw IllegalArgumentException("nothing to undo")
    }
    clearEditState()
}

private suspend fun ForData.handleDataUndoNew(out: OutputSendChannel) {
    when (val last = lastEntry) {
        is LastEntry.Compound -> {
            val name = last.name
            check(name in newEntries.compounds) {
                "unexpected: edit key $name not in compound new entries"
            }
            newEntries = newEntries.copy(compounds = newEntries.compounds - name)
            out.send("undo: new compound $name".asLine())
        }

        is LastEntry.Blend -> {
            val name = last.name
            check(name in newEntries.blends) {
                "unexpected: edit key $name not in blend new entries"
            }
            newEntries = newEntries.copy(blends = newEntries.blends - name)
            out.send("undo: new blend $name".asLine())
        }

        is LastEntry.Frequency -> {
            val name = last.name
            check(name in newEntries.frequencies) {
                "unexpected: edit key $name not in frequency new entries"
            }
            newEntries = newEntries.copy(frequencies = newEntries.frequencies - name)
            out.send("undo: new frequency $name".asLine())
        }

        null -> throw IllegalStateException("unexpected: last edit is NEW && ???")
    }
}

private suspend fun ForData.handleDataUndoUpdate(out: OutputSendChannel) {
    when (val last = lastEntry) {
        is LastEntry.Compound -> {
            val name = last.name
            check(name in updateEntries.compounds) {
                "unexpected: edit key $name not in compound update entries"
            }
            updateEntries = updateEntries.copy(compounds = updateEntries.compounds - name)
            out.send("undo: update compound $name".asLine())
        }

        is LastEntry.Blend,
        is LastEntry.Frequency,
        null -> throwUnexpectedUpdateState()
    }
}

private fun ForData.handleDataRemoveLocal() = discardRepl {
    when (val current = currentSection) {
        is CurrentSection.Compounds -> handleDataRemoveLocalCompoundX(current.compound, it)
        CurrentSection.Blends -> handleDataRemoveLocalBlend(it)
        CurrentSection.Frequencies -> handleDataRemoveLocalFrequency(it)
        null -> handleDataRemoveLocalAll(it)
    }.let { s ->
        it.outputChannel.send(s.asLine())
    }
}

private suspend fun ForData.handleDataRemoveLocalCompoundX(savedBase: CompoundBase?, st: State): String {
    val (pos, kw, in_, out) = st
    val base = savedBase ?: pos.firstOrNull()?.let(::CompoundBase)
    val all = kw["all"].toTruthy()
    val variant =
        if (all) null
        else run {
            val idx = if (base == savedBase) 0 else 1
            pos.getOrNull(idx)
        }
    when {
        base != null && !all -> {
            val cn = CompoundName(base, variant ?: "")
            newEntries = newEntries.copy(
                compounds = newEntries.compounds.filterKeys { it != cn }
            )
            updateEntries = updateEntries.copy(
                compounds = updateEntries.compounds.filterKeys { it != cn }
            )
            clearEditStateIfMatch<LastEntry.Compound, CompoundName> { it == cn }
            return "remove-local: compound $cn"
        }

        base != null && all -> {
            newEntries = newEntries.copy(
                compounds = newEntries.compounds.filterKeys { it.compound != base }
            )
            updateEntries = updateEntries.copy(
                compounds = updateEntries.compounds.filterKeys { it.compound != base }
            )
            clearEditStateIfMatch<LastEntry.Compound, CompoundName> { it.compound == base }
            return "remove-local: compound ${quote(base)} <all-variants>"
        }

        else -> {
            confirmOperation("delete all compounds", in_, out)
            clearEditStateIfMatch<LastEntry.Compound, CompoundName>(LastEdit.NEW) { it in newEntries.compounds }
            clearEditStateIfMatch<LastEntry.Compound, CompoundName>(LastEdit.UPDATE) { it in updateEntries.compounds }
            newEntries = newEntries.copy(compounds = emptyMap())
            updateEntries = updateEntries.copy(compounds = emptyMap())
            return "remove-local: <all compounds>"
        }
    }
}

private suspend fun ForData.handleDataRemoveLocalBlend(st: State): String {
    val (pos, _, in_, out) = st
    val name = pos.firstOrNull()?.let(::BlendName)
    if (name != null) {
        require(name in newEntries.blends) {
            "blend name not found"
        }
        newEntries = newEntries.copy(blends = newEntries.blends - name)
        clearEditStateIfMatch<LastEntry.Blend, BlendName>(LastEdit.NEW) { it == name }
        return "remove-local: blend ${quote(name)}"
    } else {
        confirmOperation("delete all blends", in_, out)
        clearEditStateIfMatch<LastEntry.Blend, BlendName>(LastEdit.NEW) { it in newEntries.blends }
        newEntries = newEntries.copy(blends = emptyMap())
        return "remove-local: <all blends>"
    }
}

private suspend fun ForData.handleDataRemoveLocalFrequency(st: State): String {
    val (pos, _, in_, out) = st
    val name = pos.firstOrNull()?.let(::FrequencyName)
    if (name != null) {
        require(name in newEntries.frequencies) {
            "frequency name not found"
        }
        newEntries = newEntries.copy(frequencies = newEntries.frequencies - name)
        clearEditStateIfMatch<LastEntry.Frequency, FrequencyName>(LastEdit.NEW) { it == name }
        return "remove-local: frequency ${quote(name)}"
    } else {
        confirmOperation("delete all frequencies", in_, out)
        clearEditStateIfMatch<LastEntry.Frequency, FrequencyName>(LastEdit.NEW) { it in newEntries.frequencies }
        newEntries = newEntries.copy(frequencies = emptyMap())
        return "remove-local: <all frequencies>"
    }
}

private suspend fun ForData.handleDataRemoveLocalAll(st: State): String {
    val (_, _, in_, out) = st
    confirmOperation("delete all entries", in_, out)
    newEntries = Data()
    updateEntries = DataUpdater()
    clearEditState()
    return "remove-local: <all data>"
}

private fun ForData.handleDataRemoveRemote(app: AppState) = discardRepl {

    when (val current = currentSection) {
        is CurrentSection.Compounds -> handleDataRemoveRemoteCompoundX(app, current.compound, it)
        CurrentSection.Blends -> handleDataRemoveRemoteBlend(app, it)
        CurrentSection.Frequencies -> handleDataRemoveRemoteFrequency(app, it)
        null -> handleDataRemoveRemoteAll(app, it)
    }
}

private suspend fun ForData.handleDataRemoveRemoteCompoundX(app: AppState, savedBase: CompoundBase?, st: State) {
    val (pos, kw, in_, out) = st
    val base = savedBase ?: pos.firstOrNull()?.let(::CompoundBase)
    val all = kw["all"].toTruthy()
    val variant =
        if (all) null
        else run {
            val idx = if (base == savedBase) 0 else 1
            pos.getOrNull(idx)
        }
    when {
        base != null && !all -> {
            val vnn = variant ?: ""
            val cbv = base.value.encode()
            val cvv = vnn.encode().ifBlank { "-" }
            app.doRequest(NetRequestMethod.Delete, "/api/data/compounds/$cbv/$cvv", flags = FLAGS_AUTH)
            compoundVariantsCache.getIfPresent(base)?.run {
                val filtered = buildCopy {
                    remove(vnn)
                }
                if (filtered.isNotEmpty())
                    compoundVariantsCache.put(base, filtered)
                else {
                    compoundVariantsCache.invalidate(base)
                    compoundBasesCache.updateIfModified {
                        remove(base)
                    }
                }
            }
            compoundValuesCache.invalidate(CompoundName(base, vnn))
            out.send("remove-remote: compound ${CompoundName(base, vnn)}".asLine())
        }

        base != null && all -> {
            val cbv = base.value
            app.doRequest(NetRequestMethod.Delete, "/api/data/compounds/$cbv", flags = FLAGS_AUTH)
            compoundVariantsCache.invalidate(base)
            compoundValuesCache.asDeferredMap().forEach { (k, v) ->
                if (k.compound == base) {
                    v.cancel()
                    compoundValuesCache.invalidate(k)
                }
            }
            compoundBasesCache.updateIfModified {
                remove(base)
            }
            out.send("remove-remote: compound ${quote(base)} <all-variants>".asLine())
        }

        else -> {
            confirmOperation("delete all compounds", in_, out)
            app.doRequest(NetRequestMethod.Delete, "/api/data/compounds", flags = FLAGS_AUTH)
            compoundVariantsCache.invalidateAll()
            compoundBasesCache.invalidateAll()
            compoundValuesCache.invalidateAll()
            out.send("remove-remote: all compounds".asLine())
        }
    }
}

private suspend fun ForData.handleDataRemoveRemoteBlend(app: AppState, st: State) {
    val (pos, _, in_, out) = st
    val name = pos.firstOrNull()?.let(::BlendName)
    if (name != null) {
        val bv = name.value.encode()
        app.doRequest(NetRequestMethod.Delete, "/api/data/blends/$bv", flags = FLAGS_AUTH)
        blendNamesCache.updateIfModified {
            remove(name)
        }
        blendValuesCache.invalidate(name)
        out.send("remove-remote: blend ${quote(name)}".asLine())
    } else {
        confirmOperation("delete all blends", in_, out)
        app.doRequest(NetRequestMethod.Delete, "/api/data/blends", flags = FLAGS_AUTH)
        blendNamesCache.invalidateAll()
        blendValuesCache.invalidateAll()
        out.send("remove-remote: all blends".asLine())
    }
}

private suspend fun ForData.handleDataRemoveRemoteFrequency(app: AppState, st: State) {
    val (pos, _, in_, out) = st
    val name = pos.firstOrNull()?.let(::FrequencyName)
    if (name != null) {
        val fv = name.value.encode()
        app.doRequest(NetRequestMethod.Delete, "/api/data/frequencies/$fv", flags = FLAGS_AUTH)
        frequencyNamesCache.updateIfModified {
            remove(name)
        }
        frequencyValuesCache.invalidate(name)
        out.send("remove-remote: frequency ${quote(name)}".asLine())
    } else {
        confirmOperation("delete all frequencies", in_, out)
        app.doRequest(NetRequestMethod.Delete, "/api/data/frequencies", flags = FLAGS_AUTH)
        frequencyNamesCache.invalidateAll()
        frequencyValuesCache.invalidateAll()
        out.send("remove-remote: all frequencies".asLine())
    }
}

private suspend fun ForData.handleDataRemoveRemoteAll(app: AppState, st: State) {
    val (_, _, in_, out) = st
    confirmOperation("delete all entries", in_, out)
    app.doRequest(NetRequestMethod.Delete, "/api/data", flags = FLAGS_AUTH)
    allNameCaches.forEach { it.invalidateAll() }
    allValueCaches.forEach { it.invalidateAll() }
    out.send("remove-remote: all data".asLine())
}

private fun ForData.handleImport(paginate: Paginator) = withRepl { repl, (pos, kw, in_, out) ->
    val file = pos.firstOrNull() ?: throw IllegalArgumentException("missing file")
    val current = currentSection
    if (current != null)
        confirmOperation("import only (sub)section", in_, out)
    val allowOverwrite = kw["over"].toTruthy()
    val updateMode = kw["upd"].toTruthy()
    when (current) {
        is CurrentSection.Compounds if updateMode ->
            handleImportCompoundXUpdate(current.compound, file, allowOverwrite)

        is CurrentSection.Compounds /* if !updateMode */ ->
            handleImportCompoundX(current.compound, file, allowOverwrite)

        CurrentSection.Blends ->
            handleImportBlends(file, allowOverwrite)

        CurrentSection.Frequencies ->
            handleImportFrequencies(file, allowOverwrite)

        null ->
            handleImportAll(file, allowOverwrite)
    }.let {
        paginate(repl, out, it)
    }
}

private fun ForData.handleImportCompoundXUpdate(
    savedCompound: CompoundBase?,
    file: String,
    allowOverwrite: Boolean
): List<String> =
    when {
        savedCompound != null ->
            throw IllegalArgumentException("current mode of preselected compound is not supported")
            /*
            val data = loadCompoundVariantsUpdater(file)
            val forCompound =
                updateEntries.compounds.keys
                    .mapNotNull { it.takeIf { _ -> it.compound == savedCompound }?.variant }
            if (!allowOverwrite) {
                val conflict = data.keys.intersect(forCompound)
                require(conflict.isEmpty()) {
                    "for compound updater $savedCompound, variant conflict(s): " +
                            conflict.joinToString(transform = ::quote)
                }
            }
            val newConflict =
                newEntries.compounds.keys
                    .mapNotNull { it.takeIf { _ -> it.compound == savedCompound }?.variant }
                    .let { data.keys.intersect(it) }
            require(newConflict.isEmpty()) {
                "for compound $savedCompound, variant conflict(s): " + newConflict.joinToString(transform = ::quote)
            }
            val newUpdaters = data.mapKeys { (k, _) -> CompoundName(savedCompound, k) }
            updateEntries = updateEntries.copy(compounds = updateEntries.compounds + newUpdaters)
            buildList {
                add("import update:")
                appendDataUpdater(DataUpdater(compounds = newUpdaters))
            }
            */
        else -> {
            val data = loadCompoundsUpdater(file)
            if (!allowOverwrite) {
                val conflict = updateEntries.compounds.keys.intersect(data.keys)
                require(conflict.isEmpty()) {
                    "compounds updater: conflict(s): $conflict"
                }
            }
            val newConflict =
                newEntries.compounds.keys
                    .let { data.keys.intersect(it) }
            require(newConflict.isEmpty()) {
                "for compound updater, conflict(s): $newConflict"
            }
            updateEntries = updateEntries.copy(compounds = updateEntries.compounds + data)
            buildList {
                add("import update:")
                appendDataUpdater(DataUpdater(compounds = data))
            }
        }
    }

private fun ForData.handleImportCompoundX(
    savedCompound: CompoundBase?,
    file: String,
    allowOverwrite: Boolean
): List<String> =
    when {
        savedCompound != null ->
            throw IllegalArgumentException("current mode of preselected compound is not supported")
            /*
            val data = loadCompoundVariants(file)
            val forCompound =
                newEntries.compounds.keys
                    .mapNotNull { it.takeIf { _ -> it.compound == savedCompound }?.variant }
            if (!allowOverwrite) {
                val conflict = data.keys.intersect(forCompound)
                require(conflict.isEmpty()) {
                    "for compound $savedCompound, variant conflict(s): " + conflict.joinToString(transform = ::quote)
                }
            }
            val updateConflict =
                updateEntries.compounds.keys
                    .mapNotNull { it.takeIf { _ -> it.compound == savedCompound }?.variant }
                    .let { data.keys.intersect(it) }
            require(updateConflict.isEmpty()) {
                "for compound updater $savedCompound, variant conflict(s): " +
                        updateConflict.joinToString(transform = ::quote)
            }
            // this is a less awkward name than newNewEntries
            val new = data.mapKeys { (k, _) -> CompoundName(savedCompound, k) }
            newEntries = newEntries.copy(compounds = newEntries.compounds + new)
            buildList {
                add("import:")
                appendData(Data(compounds = new))
            }
            */
        else -> {
            val data = loadCompounds(file)
            if (!allowOverwrite) {
                val conflict = newEntries.compounds.keys.intersect(data.keys)
                require(conflict.isEmpty()) {
                    "compounds: conflict(s): $conflict"
                }
            }
            val updateConflict =
                updateEntries.compounds.keys
                    .let { data.keys.intersect(it) }
            require(updateConflict.isEmpty()) {
                "for compound updater, conflict(s): $updateConflict"
            }
            newEntries = newEntries.copy(compounds = newEntries.compounds + data)
            buildList {
                add("import:")
                appendData(Data(compounds = data))
            }
        }
    }

private fun ForData.handleImportBlends(file: String, allowOverwrite: Boolean): List<String> {
    val data = loadBlends(file)
    if (!allowOverwrite) {
        val conflict = newEntries.blends.keys.intersect(data.keys)
        require(conflict.isEmpty()) {
            "blends: conflict(s): $conflict"
        }
    }
    newEntries = newEntries.copy(blends = newEntries.blends + data)
    return buildList {
        add("import:")
        appendData(Data(blends = data))
    }
}

private fun ForData.handleImportFrequencies(file: String, allowOverwrite: Boolean): List<String> {
    val data = loadFrequencies(file)
    if (!allowOverwrite) {
        val conflict = newEntries.frequencies.keys.intersect(data.keys)
        require(conflict.isEmpty()) {
            "frequencies: conflict(s): $conflict"
        }
    }
    newEntries = newEntries.copy(frequencies = newEntries.frequencies + data)
    return buildList {
        add("import:")
        appendData(Data(frequencies = data))
    }
}

private fun ForData.handleImportAll(file: String, allowOverwrite: Boolean): List<String> {
    val data = loadData(file)
    val newCompounds = data.compounds
    val newBlends = data.blends
    val newFrequencies = data.frequencies
    if (!allowOverwrite) {
        val conflictCompounds = newEntries.compounds.keys.intersect(newCompounds.keys)
        require(conflictCompounds.isEmpty()) {
            "compounds: conflict(s): $conflictCompounds"
        }
        val updateConflictCompounds =
            updateEntries
                .compounds.keys
                .let { newCompounds.keys.intersect(it) }
        require(updateConflictCompounds.isEmpty()) {
            "for compound updater, conflict(s): $conflictCompounds"
        }
        val conflictBlends = newEntries.blends.keys.intersect(newBlends.keys)
        require(conflictBlends.isEmpty()) {
            "blends: conflict(s): $conflictBlends"
        }
        val conflictFrequencies = newEntries.frequencies.keys.intersect(newFrequencies.keys)
        require(conflictFrequencies.isEmpty()) {
            "frequencies: conflict(s): $conflictFrequencies"
        }
    }
    newEntries = Data(
        newEntries.compounds + newCompounds,
        newEntries.blends + newBlends,
        newEntries.frequencies + newFrequencies
    )
    return buildList {
        add("import:")
        appendData(data)
    }
}

private fun ForData.handleCommit(app: AppState) = discardRepl { (_, _, _, out) ->
    val hasMultipleDataSections = newEntries.mapAll { if (it.isNotEmpty()) 1 else 0 }.sum() > 1
    val hasUpdates = updateEntries.isNotEmpty()
    require(hasUpdates || newEntries.isNotEmpty()) {
        "nothing to commit"
    }
    if (hasUpdates) {
        app.doRequest<Unit, Map<CompoundName, ProxyMap<CompoundInfo>>>(
            NetRequestMethod.Patch, "/api/data/compounds",
            flags = NRF_AUTH_AND_JSON,
            body = updateEntries.compounds
        )
        applyUpdatesToCompoundValuesCache(updateEntries.compounds)
        out.send("commit: updates".asLine())
    }
    val compoundsBV = newEntries.compounds.reshapeByBaseAndVariant()
    if (hasMultipleDataSections) {
        app.doRequest<Unit, Data>(
            NetRequestMethod.Post, "/api/data/compounds",
            flags = NRF_AUTH_AND_JSON,
            body = newEntries
        )
        updateCompoundRelatedCaches(newEntries.compounds, compoundsBV)
        updateBlendRelatedCaches(newEntries.blends)
        updateFrequencyRelatedCaches(newEntries.frequencies)
        out.send("commit: new".asLine())
    } else {
        val doMultipleCompounds = compoundsBV.keys.size > 1

        when {
            newEntries.compounds.isNotEmpty() && !doMultipleCompounds -> {
                val cb = newEntries.compounds.keys.first().compound
                val cbv = cb.value.encode()
                val data = compoundsBV
                app.doRequest<Unit, Map<String, CompoundInfo>>(
                    NetRequestMethod.Post, "/api/data/compounds/$cbv",
                    flags = NRF_AUTH_AND_JSON,
                    body = data.values.first(),
                )
                updateCompoundRelatedCaches(newEntries.compounds, compoundsBV)
                out.send("commit: new compound".asLine())
            }

            newEntries.compounds.isNotEmpty() && doMultipleCompounds -> {
                app.doRequest<Unit, Map<CompoundName, CompoundInfo>>(
                    NetRequestMethod.Post, "/api/data/compounds",
                    flags = NRF_AUTH_AND_JSON,
                    body = newEntries.compounds,
                )
                updateCompoundRelatedCaches(newEntries.compounds, compoundsBV)
                out.send("commit: new compounds".asLine())
            }

            newEntries.blends.isNotEmpty() -> {
                app.doRequest<Unit, Map<BlendName, BlendValue>>(
                    NetRequestMethod.Post, "/api/data/blends",
                    flags = NRF_AUTH_AND_JSON,
                    body = newEntries.blends,
                )
                updateBlendRelatedCaches(newEntries.blends)
                out.send("commit: new compounds".asLine())
            }

            newEntries.frequencies.isNotEmpty() -> {
                app.doRequest<Unit, Map<FrequencyName, FrequencyValue>>(
                    NetRequestMethod.Post, "/api/data/frequencies",
                    flags = NRF_AUTH_AND_JSON,
                    body = newEntries.frequencies,
                )
                updateFrequencyRelatedCaches(newEntries.frequencies)
                out.send("commit: new compounds".asLine())
            }

            else -> {
                throw IllegalArgumentException("commit: nothing to commit")
            }
        }
    }
}

private suspend fun ForData.applyUpdatesToCompoundValuesCache(entries: Map<CompoundName, ProxyMap<CompoundInfo>>) {
    entries.forEach { (cn, cvd) ->
        compoundValuesCache.getIfPresent(cn)
            ?.run {
                cvd.applyToObject(this)
            }?.let {
                compoundValuesCache.put(cn, it)
            }
    }
}

private suspend fun ForData.updateCompoundRelatedCaches(
    newCompounds: Map<CompoundName, CompoundInfo>,
    newCompoundsBV: Map<CompoundBase, Map<String, CompoundInfo>>
) {
    compoundBasesCache.updateIfModified {
        addAll(newCompoundsBV.keys)
    }
    newCompoundsBV.forEach { (k, vs) ->
        compoundVariantsCache.updateIfModified(k) {
            addAll(vs.keys)
        }
    }
    newCompounds.forEach { (cn, cv) ->
        compoundValuesCache.put(cn, cv)
    }
}

private suspend fun ForData.updateBlendRelatedCaches(newBlends: Map<BlendName, BlendValue>) {
    blendNamesCache.updateIfModified {
        addAll(newBlends.keys)
    }
    newBlends.forEach { (bn, bv) ->
        blendValuesCache.put(bn, bv)
    }
}

private suspend fun ForData.updateFrequencyRelatedCaches(newFrequencies: Map<FrequencyName, FrequencyValue>) {
    frequencyNamesCache.updateIfModified {
        addAll(newFrequencies.keys)
    }
    newFrequencies.forEach { (fn, fv) ->
        frequencyValuesCache.put(fn, fv)
    }
}

@JvmName("reshapeBV")
private fun CompoundsMap.reshapeByBaseAndVariant(): Map<CompoundBase, Map<String, CompoundInfo>> =
    buildMap<CompoundBase, MutableMap<String, CompoundInfo>> {
        this@reshapeByBaseAndVariant.entries.forEach { (k, v) ->
            require(!k.selectAllVariants) {
                "selectAllVariants only allowed in deletion expressions"
            }
            getOrPut(k.compound) { mutableMapOf() }
                .put(k.variant, v)
        }
    }

@JvmName($$"reshapeBV$Updater")
private fun CompoundsUpdateMap.reshapeByBaseAndVariant(): Map<CompoundBase, Map<String, ProxyMap<CompoundInfo>>> =
    buildMap<CompoundBase, MutableMap<String, ProxyMap<CompoundInfo>>> {
        this@reshapeByBaseAndVariant.entries.forEach { (k, v) ->
            require(!k.selectAllVariants) {
                "selectAllVariants only allowed in deletion expressions"
            }
            getOrPut(k.compound) { mutableMapOf() }
                .put(k.variant, v)
        }
    }

private fun LinesBuilder.appendData(d: Data, compoundFilter: CompoundBase? = null, indent: Int = 0) {
    val tabs = "\t".repeat(indent)
    val tabsNext = "\t".repeat(indent + 1)
    if (d.compounds.isNotEmpty()) {
        val compounds = d.compounds.reshapeByBaseAndVariant()
        if (compoundFilter == null) {
            add("${tabs}compounds:")
            compounds.forEach {  (base, vsInfo) ->
                appendCompoundVariants(base, vsInfo.keys, indent + 1)
            }
        } else {
            val vs = compounds[compoundFilter]
            if (vs != null && vs.isNotEmpty()) {
                appendCompoundVariants(compoundFilter, vs.keys, indent + 1)
            }
        }
    }
    if (d.blends.isNotEmpty()) {
        add("${tabs}blends:")
        d.blends.forEach {
            add("$tabsNext$it")
        }
    }
    if (d.frequencies.isNotEmpty()) {
        add("${tabs}frequencies:")
        d.frequencies.forEach {
            add("$tabsNext$it")
        }
    }
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

private fun LinesBuilder.appendDataUpdater(d: DataUpdater, compoundFilter: CompoundBase? = null, indent: Int = 0) {
    val tabs = "\t".repeat(indent)
    if (d.compounds.isNotEmpty()) {
        val compounds = d.compounds.reshapeByBaseAndVariant()
        if (compoundFilter == null) {
            add("${tabs}compounds:")
            compounds.forEach { (base, vsInfo) ->
                appendCompoundVariants(base, vsInfo.keys, indent + 1)
            }
        } else {
            val vs = compounds[compoundFilter]
            if (vs != null && vs.isNotEmpty()) {
                appendCompoundVariants(compoundFilter, vs.keys, indent + 1)
            }
        }
    }
}

private fun LinesBuilder.appendDataUpdaterSummary(d: DataUpdater, indent: Int = 0) {
    val tabs = "\t".repeat(indent)
    val entries = listOf(
        "compound entries" to d.compounds.size
    )
    entries.forEach { (name, n) ->
        if (n > 0)
            add("${tabs}${name}: $n")
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

private fun <T: Comparable<T>> LinesBuilder.appendLass(lass: ListAsSortedSet<T>, indent: Int = 1) {
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


private fun throwUnexpectedUpdateState(): Nothing =
    throw IllegalStateException("unexpected: last edit is UPDATE && !COMPOUND")

private val FLAGS_AUTH = NetRequestFlags.of(NetRequestFlag.AUTH_JWT)

