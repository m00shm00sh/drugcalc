package com.moshy.drugcalc.common

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import java.util.concurrent.Executor

@Suppress("FunctionName")
fun BoundThreadpoolDispatcher(nThreads: Int) =
    Dispatchers.Default.limitedParallelism(nThreads, "Exec-Threadpool")

/* This wouldn't be too helpful for SQLite since the blocking happens in native code, which ties up a physical thread,
 * but if it becomes an actual issue, we've scaled past the point of using SQLite in the first place.
 */
internal object VirtualThreadDispatcher : ExecutorCoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) =
        executor.execute(block)

    override val executor: Executor = Executor(Thread::startVirtualThread)
    override fun close() = error("not applicable")
}

// JDK 24 handles @Synchronized locks without thread pinning so we can use virtual threads more scalably there
@Suppress("FunctionName")
fun PreferredIODispatcher(): CoroutineDispatcher =
    if (Runtime.version().feature() >= 24) {
        VirtualThreadDispatcher
    } else
        Dispatchers.IO
