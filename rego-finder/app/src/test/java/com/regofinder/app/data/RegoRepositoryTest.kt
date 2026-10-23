package com.regofinder.app.data

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RegoRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun repository() =
        RegoRepository(PlateApi(OkHttpClient(), server.url("/")), tmp.root.resolve("history.json"), dispatcher)

    private fun enqueueSuccess() = server.enqueue(
        MockResponse.Builder().code(200).body(PlateApiTest.SUCCESS).build(),
    )

    @Test
    fun `repeat lookup is served from cache without a request`() = scope.runTest {
        enqueueSuccess()
        val repo = repository()

        val first = repo.lookup("ABC123", AuState.VIC, "k", refresh = false)
        val second = repo.lookup("ABC123", AuState.VIC, "", refresh = false)

        assertFalse((first as LookupOutcome.Found).fromCache)
        assertTrue((second as LookupOutcome.Found).fromCache)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `same plate in another state is a separate lookup`() = scope.runTest {
        enqueueSuccess()
        enqueueSuccess()
        val repo = repository()

        repo.lookup("ABC123", AuState.VIC, "k", refresh = false)
        repo.lookup("ABC123", AuState.NSW, "k", refresh = false)

        assertEquals(2, server.requestCount)
        assertEquals(listOf(AuState.NSW, AuState.VIC), repo.history.value.map { it.state })
    }

    @Test
    fun `history survives a restart`() = scope.runTest {
        enqueueSuccess()
        repository().lookup("ABC123", AuState.VIC, "k", refresh = false)

        val reopened = repository()
        reopened.load()

        assertEquals("ABC123", reopened.history.value.single().plate)
        assertTrue(reopened.lookup("ABC123", AuState.VIC, "", refresh = false) is LookupOutcome.Found)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `missing key without a cached result fails fast`() = scope.runTest {
        assertEquals(
            LookupOutcome.Failed(LookupError.MissingKey),
            repository().lookup("ABC123", AuState.VIC, " ", refresh = false),
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `plate normalisation and validation`() {
        assertEquals("ABC123", Plate.normalize(" abc-1 23"))
        assertTrue(Plate.isValid("ABC123"))
        assertFalse(Plate.isValid(""))
        assertFalse(Plate.isValid("ABCDEFGHIJK"))
        assertFalse(Plate.isValid("AB!C"))
    }
}
