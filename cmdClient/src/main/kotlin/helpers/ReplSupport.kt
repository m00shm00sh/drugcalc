package com.moshy.drugcalc.cmdclient.helpers

import com.moshy.krepl.OutputSendChannel
import com.moshy.krepl.Repl
import com.moshy.krepl.State
import com.moshy.krepl.asLine


/** Discard the extension [Repl] so that we don't modify Repl state unintentionally */
internal inline fun discardRepl(crossinline block: suspend (State) -> Unit): ReplHandler = { block(it) }

/** Shift [Repl] from receiver to argument */
internal inline fun withRepl(crossinline block: suspend (Repl, State) -> Unit): ReplHandler = { block(this, it) }

internal typealias Paginator = suspend (Repl, OutputSendChannel, List<String>) -> Unit

private typealias ReplHandler = suspend Repl.(State) -> Unit

internal suspend fun sendLinesToOutput(lines: List<String>, out: OutputSendChannel) {
    lines.forEach {
        out.send(it.asLine())
    }
}

internal typealias LinesBuilder = MutableList<String>
