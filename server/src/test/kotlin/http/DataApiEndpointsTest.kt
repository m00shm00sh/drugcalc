package com.moshy.drugcalc.server.http

//import com.moshy.drugcalc.types.calccommand
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.drugcalc.calctest.DataControllerTestSupport.RoFlag
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO as DC_RO
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_ALL
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_NONE
import com.moshy.drugcalc.server.http.util.*
import com.moshy.drugcalc.commontest.assertContains
import com.moshy.drugcalc.commontest.assertDoesNotContain
import com.moshy.drugcalc.commontest.assertEquals
import com.moshy.ProxyMap
import com.moshy.containers.assertIsSortedSet
import com.moshy.drugcalc.calctest.DataControllerTestSupport
import com.moshy.drugcalc.types.calccommand.TransformerInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
// unstarred import so it takes precedence for assert(DoesNotThrow|Throws)
import org.junit.jupiter.api.Test
import kotlin.time.*

// TODO: casefold+trim coverage for put post update del
internal class DataApiEndpointsTest {

    /*
     * /api/data POST
     */

    @Test
    fun testPostData() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val data = Data(
            compounds = mapOf(
                CompoundName(CompoundBase("hello"), "world") to
                        CompoundInfo(1.toDuration(DurationUnit.DAYS)),
                CompoundName(CompoundBase("goodbye")) to
                        CompoundInfo(2.toDuration(DurationUnit.DAYS)),
            ),
            blends = mapOf(
                BlendName("waldo") to
                        BlendValue(
                            mapOf(
                                CompoundName(CompoundBase("hello"), "world") to 6.9,
                                CompoundName(CompoundBase("testosterone"), "cypionate") to 4.20,
                            )
                        )
            ),
            frequencies = mapOf(
                FrequencyName("soon") to
                        FrequencyValue(listOf(1_000_000.toDuration(DurationUnit.DAYS)))
            )
        )
        initializeJwts()
        testEndpoint<Data, Unit>(Method.PostJson, "/api/data",
            { bearerAuth(adminJwt) }, data
        )
        testEndpoint<Unit, List<CompoundBase>>(Method.GetJson, "/api/data/compounds",
            message = "{}: updated compound names list"
        ) {
            assertContains(this, CompoundBase("goodbye"))
        }
        testEndpoint<Unit, List<BlendName>>(Method.GetJson, "/api/data/blends",
            message = "{}: updated blends list"
        ) {
            assertContains(this, BlendName("waldo"))
        }
        testEndpoint<Unit, List<FrequencyName>>(Method.GetJson, "/api/data/frequencies",
            message = "{}: updated frequencies list"
        ) {
            assertContains(this, FrequencyName("soon"))
        }
    }


    @Test
    fun `testPostData f`() =
        /* the actual reqBody type would be Map<String, Map<String, CompoundInfo | BlendValue | FrequencyValue>>
         * but the map is empty so it really doesn't matter
         */
        testExpectedFailures<Map<String, Map<String, Unit>>>(Method.PostJson, "/api/data",
            emptyMap(), listOf("{\"compounds\":[]}")
        )

    /*
     * /api/data DELETE
     */

    @Test
    fun testClearData() = withServer(RO_NONE, users, jwtConfig) {
        initializeJwts()
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data",
            { bearerAuth(adminJwt) }
        )
        testEndpoint<Unit, List<CompoundBase>>(Method.GetJson, "/api/data/compounds",
            message = "{}: compounds should be empty after delete"
        ) {
            assertTrue(isEmpty())
        }
        testEndpoint<Unit, List<BlendName>>(Method.GetJson, "/api/data/blends",
            message = "{}: blends should be empty after delete"
        ) {
            assertTrue(isEmpty())
        }
        testEndpoint<Unit, List<FrequencyName>>(Method.GetJson, "/api/data/frequencies",
            message = "{}: frequencies should be empty after delete"
        ) {
            assertTrue(isEmpty())
        }
    }

    @Test
    fun `testClearData f`() =
        testExpectedFailures<Unit>(Method.Delete, "/api/data")

    /*
     *  /api/data/compounds GET
     */

    @Test
    fun testGetCompounds() = withServer(RO_ALL) {
        testEndpoint<Unit, List<CompoundBase>>(Method.GetJson, "/api/data/compounds") {
            assertEquals(DataControllerTestSupport.expectedCompoundNames, this)
        }
    }

    /*
     * /api/data/compounds POST
     */

    @Test
    fun testPostCompounds() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val _99d = 99.toDuration(DurationUnit.DAYS)
        val _2d = 2.toDuration(DurationUnit.DAYS)
        val data = mapOf(
            // new compound with variant
            CompoundName(CompoundBase("hello"), "world") to
                CompoundInfo(1.toDuration(DurationUnit.DAYS)),
            // new compound, empty variant
            CompoundName("goodbye") to
                CompoundInfo(2.toDuration(DurationUnit.DAYS)),
            // new variant
            CompoundName(CompoundBase("testosterone"), "ligma") to
                CompoundInfo(_2d),
            // overwrite (empty) variant
            CompoundName("anavar") to
                CompoundInfo(_99d),
            )
        initializeJwts()
        testEndpoint<CompoundsMap, Unit>(Method.PostJson, "/api/data/compounds",
            { bearerAuth(adminJwt)}, data
        )
        testEndpoint<Unit, List<CompoundBase>>(Method.GetJson, "/api/data/compounds",
            message = "{}: updated compound names list"
        ) {
            assertContains(this, CompoundBase("goodbye"))
        }
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/anavar/-",
            message = "{}: updated compound variant value"
        ) {
            assertEquals(_99d, halfLife)
        }
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/testosterone/ligma",
            message = "{}: inserted new variant value"
        ) {
            assertEquals(_2d, halfLife)
        }
    }

    @Test
    fun `testPostCompounds f`() =
        testExpectedFailures<CompoundsMap>(Method.PostJson,"/api/data/compounds",
            emptyMap(), listOf(
                "{\"a\":[]}",
                "{\"a\":{\"halfLife\":\"-1d\"}}"
        ))

    /*
     * /api/data/compounds PATCH
     */

    @Test
    fun testPatchCompounds() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val newTime = 99.toDuration(DurationUnit.DAYS)
        initializeJwts()
        testEndpoint<CompoundsUpdateMap, Unit>(Method.PatchJson, "/api/data/compounds",
            { bearerAuth(adminJwt) },
            mapOf(
                CompoundName("anavar") to
                    ProxyMap<CompoundInfo>("halfLife" to newTime)
            )
        )
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/anavar/-") {
            assertEquals(newTime, halfLife)
        }
    }

    @Test
    fun `testPatchCompounds f`() =
        testExpectedFailures<Map<String, ProxyMap<CompoundInfo>>>(Method.PatchJson, "/api/data/compounds",
            emptyMap(), listOf(
                "{\"anavar\":{\"halLife\":\"-1d\"}}",
                "{\"a\":{\"halfLife\":\"-1d\"}}"
        ))

    /*
     * /api/data/compounds DELETE
     */

    @Test
    fun testDeleteCompounds() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        initializeJwts()
        // need to clear blends first to remove compounds locked by foreign key constraints
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/blends",
            { bearerAuth(adminJwt) },
        )
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/compounds",
            { bearerAuth(adminJwt) },
        )
        testEndpoint<Unit, List<CompoundBase>>(Method.GetJson, "/api/data/compounds") {
            assertTrue(isEmpty())
        }
    }

    @Test
    fun `testDeleteCompounds f`() =
        testExpectedFailures<Unit>(Method.Delete, "/api/data/compounds")

    /*
     * /api/data/compounds/{c} GET
     */

    @Test
    fun testGetCompoundBase() = withServer(RO_ALL) {
        testEndpoint<Unit, List<String>>(Method.GetJson, "/api/data/compounds/anavar") {
            assertTrue(isEmpty())
        }
        testEndpoint<Unit, List<String>>(Method.GetJson, "/api/data/compounds/+ANAVAR+",
            message = "{} (case fold and trim)"
        ) {
            assertTrue(isEmpty())
        }
        testEndpoint<Unit, List<String>>(Method.GetJson, "/api/data/compounds/testosterone") {
            assertFalse(isEmpty())
        }
        testEndpoint<Unit, Unit>(Method.GetJson, "/api/data/compounds/waldo-spam",
            responseCode = HttpStatusCode.NotFound
        )
    }

    /*
     * /api/data/compounds/{c} DELETE
     */

    @Test
    fun testDeleteCompound() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        initializeJwts()
        // DELETE /api/data/compounds/testosterone will fail because /blends/sustanon+250 locks the row
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/compounds/masteron",
            { bearerAuth(adminJwt) },
        )
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/compounds/ham",
            { bearerAuth(adminJwt) },
            responseCode = HttpStatusCode.NotFound,
            message = "{} (non-empty variant)"
        )
        testEndpoint<Unit, Unit>(Method.GetJson, "/api/data/compounds/masteron",
            responseCode = HttpStatusCode.NotFound
        )
        testEndpoint<Unit, List<CompoundBase>>(Method.GetJson, "/api/data/compounds",
        ) {
            assertDoesNotContain(this, CompoundBase("masteron"))
        }
    }

    @Test
    fun `testDeleteCompound f`() =
        testExpectedFailures<Unit>(Method.Delete, "/api/data/compounds/ham")

    /*
     * /api/data/compounds/{c}/{v} GET
     */

    @Test
    fun testGetCompoundVariant() = withServer(RO_ALL) {
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/anavar/-",
            message = "{} (empty variant)"
        )
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/testosterone/enanthate",
            message = "{} (non-empty variant)"
        )
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/testosterone/+ENANTHATE++",
            message = "{} (variant case-fold and trim)"
        )
        testEndpoint<Unit, Unit>(Method.GetJson, "/api/data/compounds/testoterone/ham",
            responseCode = HttpStatusCode.NotFound,
            message = "{} (invalid variant)"
        )
        testEndpoint<Unit, Unit>(Method.GetJson, "/api/data/compounds/ham/spam",
            responseCode = HttpStatusCode.NotFound,
            message = "{} (invalid compound)"
        )
    }

    /*
     * /api/data/compounds/{c}/{v} PUT
     */

    @Test
    fun testPutCompoundVariant() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val t = 99.toDuration(DurationUnit.DAYS)
        initializeJwts()
        testEndpoint<CompoundInfo, Unit>(Method.PutJson, "/api/data/compounds/anavar/-",
            { bearerAuth(adminJwt) }, CompoundInfo(halfLife = t),
            message = "{} (empty variant)"
        )
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/anavar/-",
            message = "{} (check empty variant)"
        ) {
           assertEquals(t, halfLife)
        }
        testEndpoint<CompoundInfo, Unit>(Method.PutJson, "/api/data/compounds/anavar/spam",
            { bearerAuth(adminJwt) }, CompoundInfo(halfLife = t),
            responseCode = HttpStatusCode.BadRequest,
            message = "{} (unexpected non-empty variant)"
        )
        testEndpoint<CompoundInfo, Unit>(Method.PutJson, "/api/data/compounds/testosterone/enanthate",
            { bearerAuth(adminJwt) }, CompoundInfo(halfLife = t),
            message = "{} (non-empty variant)"
        )
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/testosterone/enanthate",
            message = "{} (check non-empty variant)"
        ) {
            assertEquals(t, halfLife)
        }
        testEndpoint<CompoundInfo, Unit>(Method.PutJson, "/api/data/compounds/testosterone/spam",
            { bearerAuth(adminJwt) }, CompoundInfo(halfLife = t),
            message = "{} (non-empty new variant)"
        )
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/testosterone/spam",
            message = "{} (check non-empty new variant)"
        ) {
            assertEquals(t, halfLife)
        }
        testEndpoint<CompoundInfo, Unit>(Method.PutJson, "/api/data/compounds/testosterone/-",
            { bearerAuth(adminJwt) }, CompoundInfo(halfLife = t),
            responseCode = HttpStatusCode.BadRequest,
            message = "{} (unexpected empty new variant)"
        )
        testEndpoint<CompoundInfo, Unit>(Method.PutJson, "/api/data/compounds/spam/ham",
            { bearerAuth(adminJwt) }, CompoundInfo(halfLife = t),
            message = "{} (new compound, new variant)"
        )
        testEndpoint<Unit, List<String>>(Method.GetJson, "/api/data/compounds/spam",
            message = "{} (get-variants proxy)"
        ) {
            assertContains(this, "ham")
        }
        testEndpoint<CompoundInfo, Unit>(Method.PutJson, "/api/data/compounds/wham/-",
            { bearerAuth(adminJwt) }, CompoundInfo(halfLife = t),
            message = "{} (new compound, empty variant)"
        )
        testEndpoint<Unit, List<CompoundBase>>(Method.GetJson, "/api/data/compounds",
            message = "{} (get-compounds proxy)"
        ) {
            assertContains(this, CompoundBase("wham"))
        }
    }

    @Test
    fun `testPutCompoundVariant f`() =
        testExpectedFailures<CompoundInfo>(Method.PutJson, "/api/data/compounds/ham/spam",
            CompoundInfo(99.toDuration(DurationUnit.DAYS)), listOf(
                "[]",
                "{\"halfLife\":\"-1d\"}"
        ))

    /*
     * /api/data/compounds/{c}/{v} PATCH
     */

    @Test
    fun testPatchCompoundVariant() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val t = 99.toDuration(DurationUnit.DAYS)
        initializeJwts()
        testEndpoint<CompoundDetailUpdateMap, Unit>(Method.PatchJson, "/api/data/compounds/anavar/-",
            { bearerAuth(adminJwt) }, ProxyMap<CompoundInfo>("halfLife" to t),
            message = "{} (empty variant)"
        )
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/anavar/-",
            message = "{} (check empty variant)"
        ) {
            assertEquals(t, halfLife)
        }
        testEndpoint<CompoundDetailUpdateMap, Unit>(Method.PatchJson, "/api/data/compounds/anavar/spam",
            { bearerAuth(adminJwt) },ProxyMap<CompoundInfo>("halfLife" to t),
            responseCode = HttpStatusCode.NotFound,
            message = "{} (missing non-empty variant (expected empty variant))"
        )
        testEndpoint<CompoundDetailUpdateMap, Unit>(Method.PatchJson, "/api/data/compounds/testosterone/enanthate",
            { bearerAuth(adminJwt) }, ProxyMap<CompoundInfo>("halfLife" to t),
            message = "{} (non-empty variant)"
        )
        testEndpoint<Unit, CompoundInfo>(Method.GetJson, "/api/data/compounds/testosterone/enanthate",
            message = "{} (check non-empty variant)"
        ) {
            assertEquals(t, halfLife)
        }
        testEndpoint<CompoundDetailUpdateMap, Unit>(Method.PatchJson, "/api/data/compounds/testoterone/spam",
            { bearerAuth(adminJwt) },ProxyMap<CompoundInfo>("halfLife" to t),
            responseCode = HttpStatusCode.NotFound,
            message = "{} (missing non-empty variant (expected non-empty variant))"
        )
        testEndpoint<CompoundDetailUpdateMap, Unit>(Method.PatchJson, "/api/data/compounds/testoterone/-",
            { bearerAuth(adminJwt) },ProxyMap<CompoundInfo>("halfLife" to t),
            responseCode = HttpStatusCode.NotFound,
            message = "{} (missing empty variant (expected non-empty variant))"
        )
        testEndpoint<CompoundDetailUpdateMap, Unit>(Method.PatchJson, "/api/data/compounds/spam/ham",
            { bearerAuth(adminJwt) },ProxyMap<CompoundInfo>("halfLife" to t),
            responseCode = HttpStatusCode.NotFound,
            message = "{} (missing compound)"
        )
    }


    @Test
    fun `testPatchCompoundVariant f`() =
        testExpectedFailures<CompoundDetailUpdateMap>(Method.PatchJson, "/api/data/compounds/ham/spam",
            ProxyMap(CompoundInfo(99.toDuration(DurationUnit.DAYS))), listOf(
                "[]",
                "{\"halfLife\":\"-1d\"}"
        ))

    /*
     * /api/data/compounds/{c}/{v} DELETE
     */

    @Test
    fun testDeleteCompoundVariant() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        initializeJwts()
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/compounds/anavar/-",
            { bearerAuth(adminJwt) },
            message = "{} (empty variant)"
        )
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/compounds/anavar/spam",
            { bearerAuth(adminJwt) },
            responseCode = HttpStatusCode.NotFound,
            message = "{} (missing non-empty variant)"
        )
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/compounds/testosterone/enanthate",
            { bearerAuth(adminJwt) },
            message = "{} (non-empty variant)"
        )
        testEndpoint<Unit, List<String>>(Method.GetJson, "/api/data/compounds/testosterone",
            message = "{} (get-variants proxy)"
        ) {
            assertDoesNotContain(this, "enanthate")
        }
        testEndpoint<Unit, List<CompoundBase>>(Method.GetJson, "/api/data/compounds",
            message = "{} (get-compounds proxy)"
        ) {
            assertDoesNotContain(this, CompoundBase("anavar"))
            assertContains(this, CompoundBase("testosterone"))
        }
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/compounds/testosterone/-",
            { bearerAuth(adminJwt) },
            responseCode = HttpStatusCode.NotFound,
            message = "{} (missing empty variant)"
        )
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/compounds/spam/ham",
            { bearerAuth(adminJwt) },
            responseCode = HttpStatusCode.NotFound,
            message = "{} (missing compound)"
        )
    }


    @Test
    fun `testDeleteCompoundVariant f`() =
        testExpectedFailures<Unit>(Method.Delete, "/api/data/compounds/ham/spam")

    /*
     *  /api/data/blends GET
     */

    @Test
    fun testGetBlends() = withServer(RO_ALL) {
        testEndpoint<Unit, List<BlendName>>(Method.GetJson, "/api/data/blends") {
            assertEquals(DataControllerTestSupport.expectedBlendNames, this)
        }
    }

    /*
     * /api/data/blends POST
     */

    @Test
    fun testPostBlends() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val bvForSpam =
            BlendValue(mapOf(
                CompoundName("anavar") to 1.0,
                CompoundName("arimidex") to 1.0,
            ))
        val bvForS250 =
            BlendValue(mapOf(
                CompoundName("anavar") to 10.0,
                CompoundName("arimidex") to 1.0,
            ))
        val data = mapOf(
            BlendName("spam") to bvForSpam,
            BlendName("sustanon 250") to bvForS250
        )
        initializeJwts()
        testEndpoint<BlendsMap, Unit>(Method.PostJson, "/api/data/blends",
            { bearerAuth(adminJwt) }, data
        )
        testEndpoint<Unit, List<BlendName>>(Method.GetJson, "/api/data/blends") {
            assertContains(this, BlendName("spam"))
        }
        testEndpoint<Unit, BlendValue>(Method.GetJson, "/api/data/blends/spam") {
            assertEquals(bvForSpam, this)
        }
        testEndpoint<Unit, BlendValue>(Method.GetJson, "/api/data/blends/sustanon+250") {
            assertEquals(bvForS250, this)
        }
    }

    @Test
    fun `testPostBlends f`() =
        testExpectedFailures<BlendsMap>(Method.PostJson,"/api/data/blends",
            emptyMap(), listOf(
                "{\"a\":[]}",
                "{\"a\":{\"components\":{}}}"
        ))

    /*
     * /api/data/blends DELETE
     */

    @Test
    fun testDeleteBlends() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        initializeJwts()
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/blends",
            { bearerAuth(adminJwt) },
        )
        testEndpoint<Unit, List<BlendName>>(Method.GetJson, "/api/data/blends") {
            assertTrue(isEmpty())
        }
    }

    @Test
    fun `testDeleteBlends f`() =
        testExpectedFailures<Unit>(Method.Delete, "/api/data/blends")

    /*
     * /api/data/blends/{b} GET
     */

    @Test
    fun testGetBlend() = withServer(RO_ALL) {
        testEndpoint<Unit, BlendValue>(Method.GetJson, "/api/data/blends/sustanon+250")
        testEndpoint<Unit, BlendValue>(Method.GetJson, "/api/data/blends/+SUSTANON+250+")
        testEndpoint<Unit, Unit>(Method.GetJson, "/api/data/blends/ham",
            responseCode = HttpStatusCode.NotFound
        )
    }

    /*
     * /api/data/blends/{b} PUT
     */

    @Test
    fun testPutBlend() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val bvForSpam =
            BlendValue(mapOf(
                CompoundName("anavar") to 1.0,
                CompoundName("arimidex") to 1.0,
            ))
        val bvForS250 =
            BlendValue(mapOf(
                CompoundName("anavar") to 1.0,
                CompoundName("arimidex") to 1.0,
            ))
        initializeJwts()
        testEndpoint<BlendValue, Unit>(Method.PutJson, "/api/data/blends/sustanon+250",
            { bearerAuth(adminJwt) },bvForS250
        )
        testEndpoint<BlendValue, Unit>(Method.PutJson, "/api/data/blends/spam",
            { bearerAuth(adminJwt) },bvForSpam
        )
        testEndpoint<Unit, List<BlendName>>(Method.GetJson, "/api/data/blends") {
            assertContains(this, BlendName("spam"))
        }
        testEndpoint<Unit, BlendValue>(Method.GetJson, "/api/data/blends/sustanon+250",
        ) {
            assertEquals(bvForS250, this)
        }
        testEndpoint<Unit, BlendValue>(Method.GetJson, "/api/data/blends/spam",
        ) {
            assertEquals(bvForSpam, this)
        }
    }

    @Test
    fun `testPutBlend f`() =
        testExpectedFailures<BlendValue>(Method.PutJson, "/api/data/blends/spam",
            BlendValue(
                mapOf(
                    CompoundName("anavar") to 1.0,
                    CompoundName("arimidex") to 1.0,
                )
            ), listOf(
                "{\"a\":[]}",
                "{\"components\":{}}"
        ))

    /*
     * /api/data/blends/{b} DELETE
     */

    @Test
    fun testDeleteBlend() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        initializeJwts()
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/blends/sustanon+250",
            { bearerAuth(adminJwt) }
        )
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/blends/spam",
            { bearerAuth(adminJwt) },
            responseCode = HttpStatusCode.NotFound
        )
        testEndpoint<Unit, List<BlendName>>(Method.GetJson, "/api/data/blends") {
            assertDoesNotContain(this, BlendName("sustanon 250"))

        }
    }

    @Test
    fun `testDeleteBlend f`() =
        testExpectedFailures<Unit>(Method.Delete, "/api/data/blends/spam")

    /*
     *  /api/data/frequencies GET
     */

    @Test
    fun testGetFrequencies() = withServer(RO_ALL) {
        testEndpoint<Unit, List<FrequencyName>>(Method.GetJson, "/api/data/frequencies") {
            assertEquals(DataControllerTestSupport.expectedFrequencyNames, this)
        }
    }

    /*
     * /api/data/frequencies POST
     */

    @Test
    fun testPostFrequencies() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val fvForEveryDay = FrequencyValue(listOf(
            2.toDuration(DurationUnit.DAYS), 3.toDuration(DurationUnit.DAYS)
        ))
        val fvForForever = FrequencyValue(listOf(
            9999.toDuration(DurationUnit.DAYS)
        ))
        val data = mapOf(
            FrequencyName("every day") to fvForEveryDay,
            FrequencyName("forever") to fvForForever
        )
        initializeJwts()
        testEndpoint<FrequenciesMap, Unit>(Method.PostJson, "/api/data/frequencies",
            { bearerAuth(adminJwt) }, data
        )
        testEndpoint<Unit, List<FrequencyName>>(Method.GetJson, "/api/data/frequencies") {
            assertContains(this, FrequencyName("forever"))
        }
        testEndpoint<Unit, FrequencyValue>(Method.GetJson, "/api/data/frequencies/forever") {
            assertEquals(fvForForever, this)
        }
        testEndpoint<Unit, FrequencyValue>(Method.GetJson, "/api/data/frequencies/every+day") {
            assertEquals(fvForEveryDay, this)
        }
    }

    @Test
    fun `testPostFrequencies f`() =
        testExpectedFailures<FrequenciesMap>(Method.PostJson, "/api/data/frequencies/",
            emptyMap(), listOf(
            "{\"a\":[]}",
            "{\"a\":{\"values\":[]}}"
        ))

    /*
     * /api/data/frequencies DELETE
     */

    @Test
    fun testDeleteFrequencies() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        initializeJwts()
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/frequencies",
            { bearerAuth(adminJwt) },
        )
        testEndpoint<Unit, List<String>>(Method.GetJson, "/api/data/frequencies") {
            assertTrue(isEmpty())
        }
    }

    @Test
    fun `testDeleteFrequencies f`() =
        testExpectedFailures<Unit>(Method.Delete, "/api/data/frequencies")

    /*
     * /api/data/frequencies/{f} GET
     */

    @Test
    fun testGetFrequency() = withServer(RO_ALL) {
        testEndpoint<Unit, FrequencyValue>(Method.GetJson, "/api/data/frequencies/every+day")
        testEndpoint<Unit, FrequencyValue>(Method.GetJson, "/api/data/frequencies/+EVERY+DAY+")
        testEndpoint<Unit, Unit>(Method.GetJson, "/api/data/frequencies/ham",
            responseCode = HttpStatusCode.NotFound
        )
    }

    /*
     * /api/data/frequencies/{f} PUT
     */

    @Test
    fun testPutFrequency() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        val fvForEveryDay = FrequencyValue(listOf(
            2.toDuration(DurationUnit.DAYS), 3.toDuration(DurationUnit.DAYS)
        ))
        val fvForForever = FrequencyValue(listOf(
            9999.toDuration(DurationUnit.DAYS)
        ))
        initializeJwts()
        testEndpoint<FrequencyValue, Unit>(Method.PutJson, "/api/data/frequencies/every+day",
            { bearerAuth(adminJwt) }, fvForEveryDay
        )
        testEndpoint<FrequencyValue, Unit>(Method.PutJson, "/api/data/frequencies/forever",
            { bearerAuth(adminJwt) }, fvForForever
        )
        testEndpoint<Unit, List<FrequencyName>>(Method.GetJson, "/api/data/frequencies") {
            assertContains(this, FrequencyName("forever"))
        }
        testEndpoint<Unit, FrequencyValue>(Method.GetJson, "/api/data/frequencies/every+day") {
            assertEquals(fvForEveryDay, this)
        }
        testEndpoint<Unit, FrequencyValue>(Method.GetJson, "/api/data/frequencies/forever") {
            assertEquals(fvForForever, this)
        }
    }

    @Test
    fun `testPutFrequency f`() =
        testExpectedFailures<FrequencyValue>(Method.PutJson, "/api/data/frequencies/spam",
                FrequencyValue(listOf(
                    9999.toDuration(DurationUnit.DAYS)
                )), listOf(
                    "{\"a\":[]}",
                    "{\"values\":[]}"
        ))

    /*
     * /api/data/frequencies/{f}/ DELETE
     */

    @Test
    fun testDeleteFrequency() = withServer(DC_RO(RoFlag.FORBID_CLEAR), users, jwtConfig) {
        initializeJwts()
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/frequencies/every+day",
            { bearerAuth(adminJwt) }
        )
        testEndpoint<Unit, Unit>(Method.Delete, "/api/data/frequencies/spam",
            { bearerAuth(adminJwt) },
            responseCode = HttpStatusCode.NotFound
        )
        testEndpoint<Unit, List<FrequencyName>>(Method.GetJson, "/api/data/frequencies") {
            assertDoesNotContain(this, FrequencyName("every day"))
        }
    }

    @Test
    fun `testDeleteFrequency f`() =
        testExpectedFailures<Unit>(Method.Delete, "/api/data/frequencies/spam")

    /*
     *  /api/data/transformers GET
     */

    @Test
    fun testGetTransformers() = withServer(RO_ALL) {
        testEndpoint<Unit, Map<String, TransformerInfo>>(Method.GetJson, "/api/data/transformers")
    }

    private inline fun <reified T> testExpectedFailures(
        method: Method,
        endpoint: String,
        dummyBody: T = Unit as T,
        badData: List<String> = emptyList()
    ) {
        // fail due to bad flag
        withServer(RO_ALL, users, jwtConfig) {
            initializeJwts()
            testEndpoint<T, Unit>(
                method, endpoint, { bearerAuth(adminJwt) }, dummyBody,
                HttpStatusCode.Forbidden,
                "$method $endpoint should return status Forbidden when server flags are RO_ALL"
            )
        }
        withServer(RO_CLEAR_AND_SOURCE, users, jwtConfig) {
            // fail due to bad auth
            testEndpoint<T, Unit>(
                method, endpoint, { bearerAuth(userJwt) }, dummyBody,
                HttpStatusCode.Forbidden,
                "$method $endpoint should return status Forbidden when jwt lacks admin role"
            )

            testEndpoint<T, Unit>(
                method, endpoint, reqBody = dummyBody, responseCode = HttpStatusCode.Unauthorized,
                message = "$method $endpoint should return status Unauthorized when jwt is missing"
            )
            // fail due to bad data
            for (data in badData) {
                testEndpoint<String, Unit>(
                    method, endpoint, { bearerAuth(adminJwt) }, data,
                    HttpStatusCode.BadRequest,
                    "$method $endpoint should return status Bad Request when body is invalid"
                )
            }
        }
    }

    companion object {
        private val users = runBlocking { testingUserService() }
        private val jwtConfig = defaultJwtConfig()
        private val RO_CLEAR_AND_SOURCE = DC_RO(RoFlag.SOURCE, RoFlag.FORBID_CLEAR)
        private lateinit var adminJwt: String
        private lateinit var userJwt: String

        private suspend fun HttpClient.initializeJwts() {
            // initialize jwts at first instance of server
            if (!::adminJwt.isInitialized) {
                adminJwt = loginAdmin(users)
            }
            if (!::userJwt.isInitialized) {
                userJwt = loginOther(users)
            }
        }

    }
}