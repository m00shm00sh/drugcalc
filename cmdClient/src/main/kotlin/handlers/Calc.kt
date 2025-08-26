package com.moshy.drugcalc.cmdclient.handlers

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.cmdclient.helpers.*
import com.moshy.drugcalc.cmdclient.io.*
import com.moshy.drugcalc.cmdclient.states.ForCalc
import com.moshy.drugcalc.cmdclient.states.ForCalc.*
import com.moshy.drugcalc.types.calccommand.*
import com.moshy.ProxyMap
import com.moshy.containers.zipForEach
import com.moshy.drugcalc.cmdclient.io.data.WithLenientDuration
import com.moshy.krepl.*
import kotlin.time.*

internal fun AppState.configureCalc(): Repl.EntryBuilder.() -> Unit = {
    val forCalc = this@configureCalc.forCalc
    val app = this@configureCalc

    val paginator: Paginator = { repl, out, lines -> app.maybePaginate(repl, lines, out) }

    help = "enter calculation evaluator"
    handler = {
        push("calc")
        clear()

        this["setvar"] {
            help = "set calc variable"
            usage = "$name cVar value"
            handler = { (pos, _, _, out) ->
                require(pos.size == 2) {
                    "expected exactly two arguments"
                }
                val (varName, varVal) = pos
                val remapName = configRemap[varName] ?: throw IllegalArgumentException("unrecognized: $varName")
                val add = mapOf(remapName to varVal).let { ProxyMap.fromProps<Config>(it) }
                forCalc.config += add
                out.send("set $remapName".asLine())
            }
        }
        this["clearvar"] {
            usage = "$name cVar"
            help = "unset calc variable"
            handler = { (pos, _, _, out) ->
                require(pos.size == 1) {
                    "expected exactly one argument"
                }
                val varName = pos.first()
                val remapName = configRemap[varName] ?: throw IllegalArgumentException("unrecognized: $varName")
                forCalc.config -= remapName
                out.send("unset $remapName".asLine())
            }
        }
        this["clearvars"] {
            help = "unset all config overrides"
            handler = { (_, _, in_, out) ->
                confirmOperation("unset all overrides", in_, out)
                forCalc.config = ProxyMap<Config>()
                out.send("cleared config overrides".asLine())
            }
        }
        this["listvars"] {
            help = "list set vars"
            handler = { (_, _, _, out) ->
                forCalc.config.forEach { (k, v) ->
                    out.send("$k=$v".asLine())
                }
            }
        }
        this["help-var"] {
            help = "print supported vars"
            handler = { (_, _, _, out) ->
                configRemap.renderRemapEntriesTo(out)
            }
        }
        this["render-var"] {
            usage = "$name rVar value"
            help = "set render var"
            handler = { (pos, _, _, out) ->
                require(pos.size == 2) {
                    "expected exactly two arguments"
                }
                val (varName, varVal) = pos
                val remapName = renderVarRemap[varName] ?: throw IllegalArgumentException("unrecognized: $varName")
                renderVarSetter[remapName]!!.invoke(forCalc, varVal)
                out.send("set render var $remapName".asLine())
            }
        }
        this["help-render-var"] {
            help = "print supported render vars"
            handler = { (_, _, _, out) ->
                renderVarRemap.renderRemapEntriesTo(out)
            }
        }
        this["add"] {
            usage = "$name {arg=val}+"
            help = "add cycle to list"
            handler = { (_, kw, _, out) ->
                val data = parseCycleDescription(kw)
                forCalc.reqCycles += data
                out.send("add [${forCalc.reqCycles.size - 1}]".asLine())
            }
        }
        this["del"] {
            help = "delete cycle from list"
            usage = "$name position"
            handler = { (pos, _, _, out) ->
                require(pos.size == 1) {
                    "expected exactly one argument"
                }
                val index = pos.first().toIntOrNull() ?: throw IllegalArgumentException("expected integer argument")
                require (index in 0..<forCalc.reqCycles.size) {
                    "index out of range ($index; size=${forCalc.reqCycles.size})"
                }
                forCalc.reqCycles = forCalc.reqCycles.toMutableList().apply { removeAt(index) }
                out.send("del [$index]".asLine())
            }
        }
        this["edit"] {
            help = "replace cycle at position"
            usage = "$name position {arg=val}+"
            handler = { (pos, kw, _, out) ->
                require(pos.size == 1) {
                    "expected exactly one argument"
                }
                val index = pos.first().toIntOrNull() ?: throw IllegalArgumentException("expected integer argument")
                require (index in 0..<forCalc.reqCycles.size) {
                    "index out of range ($index; size=${forCalc.reqCycles.size})"
                }
                val data = parseCycleDescription(kw)
                forCalc.reqCycles = forCalc.reqCycles.toMutableList().apply { this[index] = data }
                out.send("edit [$index]".asLine())
            }
        }
        this["clear"] {
            help = "clear the list of cycles"
            handler = { (_, _, in_, out) ->
                confirmOperation("clear all cycles", in_, out)
                forCalc.reqCycles = emptyList()
                out.send("clear cycles".asLine())
            }
        }
        this["eval"] {
            help = "evaluate cycle"
            handler = withRepl { repl, (_, _, _, out) ->
                val reqBody = CycleRequest(
                    app.forData.newEntries,
                    ForCalc.DEFAULT_CONFIG + forCalc.config,
                    forCalc.reqCycles
                )
                val resp = app.doRequest<DecodedCycleResult, _>(NetRequestMethod.Post, "/api/calc", body = reqBody)
                buildList {
                    add("eval: ${resp.size} items:")
                    resp.forEach { (k, v) ->
                        add("${quote(k)}: [${v.x.size}]{${v.type}}")
                    }
                }.let {
                    paginator(repl, out, it)
                }
                forCalc.calcResult = resp
            }
        }
        this["result"] {
            help = "show result"
            handler = withRepl { repl, (pos, _, _, out) ->
                val selection = pos.firstOrNull()
                val lines = forCalc.renderResult(selection)
                paginator(repl, out, lines)
            }
        }
    }
}

private fun parseCycleDescription(kvToks: Map<String, String>): CycleDescription =
    buildMap {
        kvToks.forEach { (k, v) ->
            val remapK = descriptionRemap[k] ?: throw IllegalArgumentException("unrecognized: $k")
            this[remapK] = v
        }
    }.let {
        val map = ProxyMap.fromProps<CycleDescription>(it, module = WithLenientDuration)
        map.createObject()
    }

private fun ForCalc.renderResult(selection: String?) =
    buildList list@ {
        with(this@renderResult) {
            val lineDuration = lineDuration
                if (selection != null) {
                val result = calcResult[selection]
                    ?: throw IllegalArgumentException("no data for selection: ${quote(selection)}")
                this@list.renderResultItem(result, lineDuration, 0)
            } else {
                calcResult.forEach { (name, elems) ->
                    add("$name:")
                    renderResultItem(elems, lineDuration, 1)
                }
            }
        }
    }

private fun LinesBuilder.renderResultItem(elems: DecodedXYList, lineDuration: Duration, indent: Int = 1) {
    val tabs = "\t".repeat(indent)
    if (elems.x.isEmpty())
        return
    var lineStart: Duration = -1 * lineDuration
    data class LineEntry(
        val t0Str: String,
        val ys: MutableList<Double> = mutableListOf()
    )
    val entries = buildList {
        elems.x.zipForEach(elems.y) { x, y ->
            if ((x - lineStart) >= lineDuration) {
                lineStart = x
                add(LineEntry("$x"))
            }
            last().ys.add(y)
        }
    }
    val maxXStrLen = entries.maxOf { it.t0Str.length }
    entries.forEach { (x, ys) ->
        buildString {
            append(tabs)
            (0..<(maxXStrLen - x.length)).forEach { _ ->
                append(' ')
            }
            append(x)
            append('|')
            ys.joinTo(this, separator = " ") {
                String.format("%.3f", it)
            }
        }.let {
            add(it)
        }
    }
}

private val configRemap = mapOf(
    id(name(Config::tickDuration)),
    aliasName("tick", Config::tickDuration),
    aliasName("td", Config::tickDuration),
    id(name(Config::cutoffMilligrams)),
    aliasName("cutoff", Config::cutoffMilligrams),
    aliasName("cut", Config::cutoffMilligrams),
    aliasName("co", Config::cutoffMilligrams),
    id(name(Config::doLambdaDoseCorrection)),
    aliasName("lambdac", Config::doLambdaDoseCorrection),
    aliasName("lambdacorrection", Config::doLambdaDoseCorrection),
)

private val descriptionRemap = mapOf(
    id(name(CycleDescription::prefix)),
    aliasName("fl", CycleDescription::prefix),
    aliasName("flags", CycleDescription::prefix),
    id(name(CycleDescription::compoundOrBlend)),
    aliasName("cb", CycleDescription::compoundOrBlend),
    aliasName("compound", CycleDescription::compoundOrBlend),
    aliasName("blend", CycleDescription::compoundOrBlend),
    id(name(CycleDescription::variantOrTransformer)),
    aliasName("vx", CycleDescription::variantOrTransformer),
    aliasName("var", CycleDescription::variantOrTransformer),
    aliasName("trans", CycleDescription::variantOrTransformer),
    id(name(CycleDescription::dose)),
    aliasName("d", CycleDescription::dose),
    id(name(CycleDescription::start)),
    aliasName("s", CycleDescription::start),
    id(name(CycleDescription::duration)),
    aliasName("t", CycleDescription::duration),
    aliasName("dur", CycleDescription::duration),
    id(name(CycleDescription::freqName)),
    aliasName("fn", CycleDescription::freqName),
    aliasName("freq", CycleDescription::freqName),
)

private val renderVarRemap = mapOf(
    id(name(ForCalc::lineDuration)),
    aliasName("line", ForCalc::lineDuration)
)

private val renderVarSetter = mapOf(
    name(ForCalc::lineDuration) to { c: ForCalc, s: String -> c.lineDuration = Duration.parse(s) }
)

private suspend fun Map<String, String>.renderRemapEntriesTo(out: OutputSendChannel) {
    entries.forEach { (k, v) ->
        when {
            k == v -> quote(k)
            else -> "${quote(k)} (for ${quote(v)})"
        }.let {
            out.send(it.asLine())
        }
    }
}