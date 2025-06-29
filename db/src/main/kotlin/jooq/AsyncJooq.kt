package com.moshy.drugcalc.db.jooq

import com.moshy.drugcalc.db.jooq.AsyncJooq.AnyQuery
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.jetbrains.annotations.Blocking
import org.jooq.*
import org.jooq.exception.*
import org.jooq.kotlin.coroutines.transactionCoroutine
import org.sqlite.SQLiteException
import java.sql.BatchUpdateException
import java.sql.SQLException

/** Ties a JOOQ [DSLContext] to a [CoroutineContext] that executes blocking IO. */
internal class AsyncJooq(
    private val dsl: DSLContext,
    private val ctx: CoroutineContext
) {
    /** Suspend blocking operation with context onto current continuation.
     *
     * @see suspendedBlockingIO for non-result version
     * @see withContext
     */
    suspend fun <T : Record, R> ResultQuery<T>.suspendedBlockingFetch(block: ResultQuery<T>.() -> R): R {
        return withContext(ctx) {
            runInterruptible {
                block()
            }
        }
    }

    /** Suspend blocking operation with context onto current continuation.
     *
     * @see suspendedBlockingFetch for result version
     * @see withContext
     */
    suspend fun <R> Query.suspendedBlockingIO(block: Query.() -> R): R {
        return withContext(ctx) {
            runInterruptible {
                block()
            }
        }
    }

    /** Do something with a [DSLContext] provided as receiver. */
    fun <R> dsl(block: DSLContext.() -> R): R =
        dsl.block()

    /** Launch a transaction coroutine with instance context, provided a list of [queryFactories].
     *
     * Inside the transaction, for each query in [queryFactories], if the coroutine is still active,
     * execute the statement, then save the row count if the statement was tagged.
     *
     * Each statement is a factory taking the transaction context as a receiver and returning an [AnyQuery].
     *
     * For convenience, if there is exactly one statement in the argument list and it's not a result query,
     * it's considered a tagged query.
     * If there are zero arguments, no transaction is performed.
     */
    suspend fun suspendedBlockingTransaction(
        queryFactories: List<QueryFactory>
    ): List<Int> {
        /* can't offload to transactionResult because we will have a suspend -> block -> suspend chain,
          * which won't work with coroutines and we need the transaction manager to capture cancellation
          */
        require(queryFactories.isNotEmpty()) {
            "missing transaction statements"
        }
        return dsl.transactionCoroutine(ctx) { c ->
            val ctx = c.dsl()
            queryFactories.mapNotNull {
                val anyQ = it.invoke(ctx)
                val rows = runInterruptible { anyQ.execute() }
                rows.takeIf { _ -> anyQ.isTagged || (queryFactories.size == 1 && !anyQ.isResultQuery) }
            }
        }
    }

    suspend inline fun suspendedBlockingTransaction(
        vararg queryFactories: QueryFactory
    ): List<Int> =
        suspendedBlockingTransaction(queryFactories.asList())


    /** Base class unifying fetch, single, and batch statement results.
     *
     * If [isTagged] is true, save the row count from execution.
     */
    abstract class AnyQuery(val isTagged: Boolean = false, val isResultQuery: Boolean = false) {
        @Blocking
        abstract fun execute(): Int

    }

    fun <R : Record> ResultQuery<R>.asResultQuery(op: ResultQuery<R>.() -> Unit) =
        object : AnyQuery(false, true) {
            override fun execute(): Int {
                this@asResultQuery.op()
                return 1
            }
        }

    fun Query.asAnyQuery() = object : AnyQuery(false) {
        override fun execute(): Int = this@asAnyQuery.execute()
    }

    fun Batch.asAnyQuery() = object : AnyQuery(false) {
        override fun execute(): Int = this@asAnyQuery.execute().sum()
    }

    fun Query.asTaggedAnyQuery() = object : AnyQuery(true) {
        override fun execute(): Int = this@asTaggedAnyQuery.execute()
    }

    fun List<Query>.asTaggedAnyQuery() = object : AnyQuery(true) {
        override fun execute(): Int =
            this@asTaggedAnyQuery.sumOf { it.execute() }
    }

    /** Evaluate [block], translating SQL exceptions.
     * @throws IllegalArgumentException if executing the SQL statement caused an integrity constraint violation
     * @throws IllegalStateException if executing the SQL statement caused any other kind of error
     * @throws CancellationException if coroutine was cancelled or otherwise interrupted
     */
    suspend fun <R> op(block: suspend AsyncJooq.() -> R): R {
        try {
            return block()
        } catch (ex: DataAccessException) {
            throw translateJooqException(ex)
        }
    }
}

internal typealias QueryFactory = DSLContext.() -> AnyQuery

private fun translateJooqException(t: DataAccessException): Throwable {
    if (t is IntegrityConstraintViolationException)
        return IllegalArgumentException("Database exception: ${t.message}", t.cause)
    val cause = t.cause
    if (cause is BatchUpdateException) {
        val cause2 = checkNotNull(cause.cause as? SQLException)
        // for sqlite, figure out if the bulk operation failed due to table constraint
        // FIXME: adapt to postgres
        if (cause2 is SQLiteException && cause2.errorCode == 19)
            return IllegalArgumentException("Database exception during bulk op: ${t.message}", t.cause)
    }
    return IllegalStateException("Database exception: ${t.message}", t.cause)
}