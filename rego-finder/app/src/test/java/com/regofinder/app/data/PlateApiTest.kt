package com.regofinder.app.data

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlateApiTest {
    private val server = MockWebServer()
    private lateinit var api: PlateApi

    @Before
    fun setUp() {
        server.start()
        api = PlateApi(OkHttpClient(), server.url("/"), clock = { 1_000L })
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `success returns best match first then alternatives`() = runTest {
        server.enqueue(json(200, SUCCESS))

        val outcome = api.lookup("ABC123", AuState.VIC, "pk_test")

        val record = (outcome as LookupOutcome.Found).record
        assertEquals(listOf(5152708L, 1857621L), record.vehicles.map { it.vehicleId })
        assertEquals("TOYOTA", record.vehicles[0].make)
        assertEquals("2003-2006", record.vehicles[0].yearRange)
        assertEquals("multiple", record.match)
        assertEquals(1_000L, record.fetchedAt)

        val request = server.takeRequest()
        assertEquals("/api/v1/lookup", request.url.encodedPath)
        assertEquals("ABC123", request.url.queryParameter("plate"))
        assertEquals("VIC", request.url.queryParameter("state"))
        assertEquals("true", request.url.queryParameter("detailed"))
        assertEquals("pk_test", request.headers["X-API-Key"])
    }

    @Test
    fun `not found body code maps to NotFound`() = runTest {
        server.enqueue(json(200, """{"success":false,"vehicle":null,"code":"not_found","error":"No vehicle found"}"""))
        assertEquals(LookupOutcome.Failed(LookupError.NotFound), api.lookup("ZZZ999", AuState.NSW, "k"))
    }

    @Test
    fun `http errors map to typed errors`() = runTest {
        server.enqueue(json(401, """{"detail":"Invalid API key"}"""))
        server.enqueue(json(429, """{"detail":"Monthly quota exceeded"}""").newBuilder().addHeader("Retry-After", "3600").build())
        server.enqueue(json(503, """{"success":false,"code":"lookup_failed"}""").newBuilder().addHeader("Retry-After", "5").build())
        server.enqueue(json(400, """{"detail":"Invalid state"}"""))

        assertEquals(LookupOutcome.Failed(LookupError.InvalidKey), api.lookup("A", AuState.NSW, "k"))
        assertEquals(LookupOutcome.Failed(LookupError.RateLimited(3600)), api.lookup("A", AuState.NSW, "k"))
        assertEquals(LookupOutcome.Failed(LookupError.Unavailable(5)), api.lookup("A", AuState.NSW, "k"))
        assertEquals(LookupOutcome.Failed(LookupError.Rejected("Invalid state")), api.lookup("A", AuState.NSW, "k"))
    }

    @Test
    fun `network failure maps to Network`() = runTest {
        server.close()
        val outcome = api.lookup("A", AuState.NSW, "k")
        assertTrue(outcome is LookupOutcome.Failed && outcome.error is LookupError.Network)
    }

    private fun json(code: Int, body: String) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    companion object {
        // Shape from https://plateapi.com.au/docs/lookup/ (example response), plus an unknown field.
        val SUCCESS = """
            {
              "success": true,
              "vehicle": {
                "year_range": "2003-2006", "lowest_year": 2003, "highest_year": 2006,
                "make": "TOYOTA", "model": "COROLLA",
                "description": "03~06 TOYOTA COROLLA SPORTIVO 1.8L PETROL",
                "body": "HATCHBACK", "engine": "1.8L", "vehicle_id": 5152708
              },
              "alternatives": [
                {
                  "year_range": "2003-2006", "lowest_year": 2003, "highest_year": 2006,
                  "make": "TOYOTA", "model": "COROLLA",
                  "description": "03~06 TOYOTA COROLLA ASCENT 1.8L PETROL",
                  "body": "SEDAN", "engine": "1.8L", "vehicle_id": 1857621
                }
              ],
              "match": "multiple",
              "source": "plateapi",
              "duration_ms": 1509.96,
              "request_id": "req_7f3a9c1b4e8d2f6a0c5e3d1b",
              "something_new": {"ignored": true}
            }
        """.trimIndent()
    }
}
