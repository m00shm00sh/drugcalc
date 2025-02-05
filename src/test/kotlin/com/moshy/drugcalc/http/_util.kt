package com.moshy.drugcalc.http

import com.moshy.drugcalc.io.RevisionSummary
import com.moshy.drugcalc.testutil.CheckArg
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.provider.Arguments
import kotlin.test.assertEquals

// similar to RepositoryTest.GetRevisionsP but we relax the typing from Int? to String? to test more-invalid values
internal class GetRevisionsP(val until: String? = null, val limit: String? = null)

internal fun constructDiffstatQueryString(selector: String, grp: GetRevisionsP) =
    buildString {
        append("/api/$selector/diffstat")
        if (grp.until != null || grp.limit != null) {
            append("?")
        }
        if (grp.until != null)
            append("until=${grp.until}")
        if (grp.until != null && grp.limit != null)
            append("&")
        if (grp.limit != null)
            append("limit=${grp.limit}")
    }

internal suspend inline fun <reified T> HttpResponse.toResultOrException(typeInfo: TypeInfo = typeInfo<T>()) =
    when (status) {
        HttpStatusCode.OK -> body<T>(typeInfo)
        HttpStatusCode.BadRequest -> throw IllegalArgumentException(bodyAsText())
        HttpStatusCode.NotFound -> throw NoSuchElementException(bodyAsText())
        else -> throw IllegalStateException("unexpected http status $status")
    }

/* NOTE: this is a weaker test than in RepositoryTest. We only care that
 *       1. revision number is as expected
 *       2. the stats aren't empty
 *       The function supplied as `check2` satisfies (2) with some kind of assertXxx call.
 */
internal fun <T: RevisionSummary> testArgsForDiffStatFactory(check2: (T) -> Unit) =
        listOf(
            arrayOf("(u=1, lim=1) => [r1]",
                CheckArg.nothrow<List<T>, _>(GetRevisionsP(until = "1", limit = "1")) {
                    assertAll(
                        { assertEquals(1, it.size) },
                        { assertEquals(1, it[0].revision) },
                        { check2(it[0]) }
                    )
                }
            ),
            arrayOf("(u=2, lim=1) => [r2]",
                CheckArg.nothrow<List<T>, _>(GetRevisionsP(until = "2", limit = "1")) {
                    assertAll(
                        { assertEquals(1, it.size) },
                        { assertEquals(2, it[0].revision) },
                        { check2(it[0]) }
                    )
                }
            ),
            arrayOf("(u=2, lim=2) => [r2, r1]",
                CheckArg.nothrow<List<T>, _>(GetRevisionsP(until = "2", limit = "2")) {
                    val exp = listOf(2, 1)
                    val got = it.map { r -> r.revision }
                    assertAll(
                        { assertEquals(2, it.size) },
                        { assertEquals(exp, got) },
                        { for (r in it) check2(r) }
                    )
                }
            ),
            arrayOf("(u<0)",
                CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(until = "-1"), "until")
            ),
            arrayOf("(u=0)",
                CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(until = "0"), "until")
            ),
            arrayOf("(u=NaN)",
                CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(until = "fred"), "until")
            ),
            arrayOf("(l<0)",
                CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(limit = "-1"), "limit")
            ),
            arrayOf("(l=0)",
                CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(limit = "0"), "limit")
            ),
            arrayOf("(l=NaN)",
                CheckArg.throws<IllegalArgumentException, _>(GetRevisionsP(limit = "fred"), "limit")
            ),
        )
            .map { Arguments.of(*it) }
