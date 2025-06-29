package com.moshy.drugcalc.commontest

import org.junit.jupiter.api.Assertions.*
import kotlin.reflect.KClass

/**
 * If `invoke { $this = args; func(...) } is expected to throw an [expectedException],
 * assert [expectedException] is thrown and run [resultValueMatcher] on the exception message.
 * If not, assert nothing is thrown and run [resultValueMatcher] on the result.
 *
 * [resultValueMatcher] is expected to have its own value assertion logic.
 *
 */
class CheckArg<R, ParamsContainer> private constructor(
    private val args: ParamsContainer,
    private val expectedException: KClass<out Throwable>?,
    private val resultValueMatcher: (R) -> Unit,
    private val exceptionMessageMatcher: (String?) -> Unit,
    private val asserterMessage: String
) {
    fun assertIsSetupForNothrow() = assertNull(expectedException)
    fun assertIsSetupForThrow() = assertNotNull(expectedException)

    fun invoke(func: ParamsContainer.() -> R) {
        val result =
            try {
                Result.success(args.func())
            } catch (t: Throwable) {
                Result.failure(t)
            }
        if (expectedException != null) {
            assertThrows(expectedException.java, result::getOrThrow, asserterMessage)
            exceptionMessageMatcher(result.exceptionOrNull()!!.message)
            @Suppress("UNCHECKED_CAST") // R will always be Unit for throwing instances
            resultValueMatcher(Unit as R)
        } else {
            assertDoesNotThrow(result::getOrThrow, asserterMessage)
            resultValueMatcher(result.getOrThrow())
        }
    }

    suspend fun invokeSuspend(func: suspend ParamsContainer.() -> R) {
        val result =
            try {
                Result.success(args.func())
            } catch (t: Throwable) {
                Result.failure(t)
            }

        if (expectedException != null) {
            assertThrows(expectedException.java, result::getOrThrow, asserterMessage)
            exceptionMessageMatcher(result.exceptionOrNull()!!.message)
        } else {
            assertDoesNotThrow(result::getOrThrow, asserterMessage)
            resultValueMatcher(result.getOrThrow())
        }
    }

    fun withPostThrowHandler(block: () -> Unit): CheckArg<Unit, ParamsContainer> {
        require(expectedException != null) {
            "cannot add post-throw handler to non-throwing CheckArg"
        }
        return CheckArg(
            args, expectedException, { block() },
            exceptionMessageMatcher, asserterMessage
        )
    }

    fun withNewResultValueMatcher(newRVMatcher: (R) -> Unit) =
        CheckArg(args, expectedException, newRVMatcher, exceptionMessageMatcher, asserterMessage)

    companion object {
        /* reifying quasi-constructors */
        /** [CheckArg] expecting an exception with Regex message matcher. */
        inline fun <reified Th : Throwable, ParamsContainer> throws(
            args: ParamsContainer,
            matchPat: Regex,
            message: String = matchPat.toString()
        ) =
            throws(args, Th::class,
                { assertContains(matchPat, it, message) },
                message
            )


        /** [CheckArg] expecting an exception with String message matcher. */
        inline fun <reified Th : Throwable, ParamsContainer> throws(
            args: ParamsContainer,
            matchSubstr: String = "",
            message: String = matchSubstr
        ) =
            if (matchSubstr.isNotEmpty())
                throws(
                    args,
                    Th::class,
                    { assertContains(matchSubstr, it, message) },
                    message
                )
            else
                throws(args, Th::class, { /* ex.message */ }, message)

        fun <ParamsContainer> throws(
            args: ParamsContainer,
            exceptionClass: KClass<out Throwable>,
            exceptionMessageMatcher: (String?) -> Unit,
            message: String
        ) =
            CheckArg<Any?, ParamsContainer>(
                args, exceptionClass,
                { /* rv */ },
                exceptionMessageMatcher,
                message
            )

        /** [CheckArg] not expecting an exception with result value matcher. */
        fun <R, ParamsContainer> nothrow(
            args: ParamsContainer,
            message: String = "",
            matcher: (R) -> Unit
        ) =
            CheckArg<R, ParamsContainer>(
                args, null, { matcher(it) },
                { /* ex.message */ },
                message
            )

        /** [CheckArg] not expecting an exception and discarding return value. */
        fun <ParamsContainer> nothrow(args: ParamsContainer, message: String = "") =
            nothrow<Any?, ParamsContainer>(args, message) { /* matcher */ }

    }
}
