package com.moshy.drugcalc.cmdclient.handlers

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.cmdclient.helpers.*
import com.moshy.drugcalc.cmdclient.io.*
import com.moshy.drugcalc.cmdclient.io.data.WithLenientDuration
import com.moshy.drugcalc.cmdclient.states.ForCalc
import com.moshy.drugcalc.cmdclient.states.ForCalc.*
import com.moshy.drugcalc.common.PreferredIODispatcher
import com.moshy.drugcalc.types.calccommand.*
import com.moshy.ProxyMap
import com.moshy.containers.zipForEach
import com.moshy.drugcalc.common.toTruthy
import com.moshy.krepl.*
import kotlinx.coroutines.withContext
import kotlinx.html.Entities
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.stream.createHTML
import space.kscience.dataforge.meta.configure
import space.kscience.plotly.*
import space.kscience.plotly.models.*
import space.kscience.visionforge.html.appendTo
import java.io.File
import kotlin.time.*
import kotlin.time.Duration.Companion.days

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
            usage = "$name {flags=.b|.t}? cb=compound {vx=var|xfrm}? {d=dose}? s=time t=time fn=freqname"
            help = "add cycle to list"
            handler = { (_, kw, _, out) ->
                val data = parseCycleDescription(kw, null)
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
                val data = parseCycleDescription(kw, forCalc.reqCycles[index])
                forCalc.reqCycles = forCalc.reqCycles.toMutableList().apply { this[index] = data }
                out.send("edit [$index]".asLine())
            }
        }
        this["show"] {
            help = "show cycle"
            handler = { (_, _, _, out) ->
                forCalc.reqCycles.forEachIndexed { i, v ->
                    out.send("[$i]: $v".asLine())
                }
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
                val config = ForCalc.DEFAULT_CONFIG + forCalc.config
                val reqBody = CycleRequest(
                    app.forData.newEntries,
                    config,
                    forCalc.reqCycles
                )
                val resp: CycleResult =
                    app.doRequest<_, _>(NetRequestMethod.Post, "/api/calc?noDecode=true", body = reqBody)
                forCalc.calcTimeTick = config["tickDuration"] as Duration
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
                forCalc.checkForExpectedResult()
                val selection = pos.firstOrNull()
                val lines = forCalc.renderResult(selection)
                paginator(repl, out, lines)
            }
        }
        this["render"]  {
            help = "render to file"
            handler = discardRepl { (pos, kw, _, out) ->
                forCalc.checkForExpectedResult()
                val file = pos.firstOrNull() ?: throw IllegalArgumentException("expected filename")
                val split = kw["sp"].toTruthy()
                forCalc.renderPlotToFile(file, split)
                out.send("saved $file".asLine())
            }
        }
    }
}

private fun parseCycleDescription(kvToks: Map<String, String>, old: CycleDescription?): CycleDescription =
    buildMap {
        kvToks.forEach { (k, v) ->
            val remapK = descriptionRemap[k] ?: throw IllegalArgumentException("unrecognized: $k")
            this[remapK] = v
        }
    }.let {
        val map = ProxyMap.fromProps<CycleDescription>(it, module = WithLenientDuration)
        if (old != null)
            map.applyToObject(old)
        else
            map.createObject()
    }

private fun ForCalc.checkForExpectedResult() =
    require (calcResult.isNotEmpty() || reqCycles.isEmpty()) {
        "expected result; did you forget to call `eval`?"
    }

private fun ForCalc.renderResult(selection: String?) =
    buildList list@ {
        with(this@renderResult) {
            // FIXME: refactor calc:datacontroller/Decoder.kt:Duration.transcode() to shared requireToInt()
            val lineDuration = (lineDuration / calcTimeTick).toInt()
            val temporals = TextRendererTemporals(lineDuration, calcTimeTick)
            if (selection != null) {
                val result = calcResult[selection]
                    ?: throw IllegalArgumentException("no data for selection: ${quote(selection)}")
                this@list.renderResultItem(result, temporals, 0)
            } else {
                calcResult.forEach { (name, elems) ->
                    add("$name:")
                    renderResultItem(elems, temporals, 1)
                }
            }
        }
    }

private fun LinesBuilder.renderResultItem(elems: XYList, t: TextRendererTemporals, indent: Int = 1) {
    val tabs = "\t".repeat(indent)
    if (elems.x.isEmpty())
        return
    var lineStart: Int = -1
    data class LineEntry(
        val t0Str: String,
        val ys: MutableList<Double> = mutableListOf()
    )
    val entries = buildList {
        elems.x.zipForEach(elems.y) { x, y ->
            if ((x - lineStart) >= t.lineDuration) {
                lineStart = x
                add(LineEntry("${x * t.tickDuration}"))
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

private data class TextRendererTemporals(
    val lineDuration: Int,
    val tickDuration: Duration,
)

private suspend fun ForCalc.renderPlotToFile(filename: String, split: Boolean = false) {
    val dayInTicksF = (1.days / calcTimeTick)
    val resultShapes = calcResult.mapValues { it.value.type }
    val lineItemNames = mutableListOf<String>()
    val stepItemNames= mutableListOf<String>()

    resultShapes.forEach { (k, v) ->
        when (v) {
            XYList.PlotType.POINT -> lineItemNames.add(k)
            XYList.PlotType.BAR -> stepItemNames.add(k)
        }
    }

    val xVals = calcResult.entries.associate { (k, v) -> k to v.x.map { x -> x / dayInTicksF } }
    val yVals = calcResult.entries.associate { (k, v) -> k to v.y }

    val p = Plotly.plot {
        lineItemNames.forEach {
            scatter {
                x.numbers = xVals[it]!!
                y.numbers = yVals[it]!!
                name = it
                showlegend = true
                mode = ScatterMode.lines
                type = TraceType.scattergl
            }
        }
        stepItemNames.forEach {
            scatter {
                x.numbers = xVals[it]!!
                y.numbers = yVals[it]!!
                name = it
                showlegend = true
                mode = ScatterMode.lines
                type = TraceType.scattergl
                line {
                    shape = LineShape.hv
                }
            }
        }
        layout {
            xaxis {
                title = "time (days)"
            }
            yaxis {
                title = "release rate (mg/day)"
                showgrid = true

            }
            title = "Dose release estimate"
            legend {
                title = "compound"
                orientation = LegendOrientation.horizontal
                xanchor = XAnchor.center
                x = 0.5
            }
            hovermode = HoverMode.`x unified`
        }
    }
    if (split) {
        val head: String = createHTML().head {
            cdnPlotlyHeader.appendTo(consumer)
        }
        val hxBody: String = StaticPlotlyRenderer.run {
            createHTML().body {
                renderPlot(p, "hxChart", PlotlyConfig()).toString()
            }
        }
        withContext(PreferredIODispatcher()) {
            File($$"$$filename$head").writeText(head)
            File($$"$$filename$body").writeText(hxBody)
        }
    } else {
        val h = p.toHTML()
        withContext(PreferredIODispatcher()) {
            File(filename).writeText(h)
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