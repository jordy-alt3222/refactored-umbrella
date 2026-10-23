package com.regofinder.app.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class Vehicle(
    val make: String? = null,
    val model: String? = null,
    val description: String? = null,
    @SerialName("year_range") val yearRange: String? = null,
    val body: String? = null,
    val engine: String? = null,
    @SerialName("detailed_description") val detailedDescription: String? = null,
    @SerialName("vehicle_id") val vehicleId: Long? = null,
)

/** A successful lookup. [vehicles] holds the best match first, then PlateAPI's alternatives. */
@Serializable
data class RegoRecord(
    val plate: String,
    val state: AuState,
    val vehicles: List<Vehicle>,
    /** "exact", "multiple" or "ambiguous". */
    val match: String? = null,
    val fetchedAt: Long,
)

sealed interface LookupError {
    data object MissingKey : LookupError
    data object InvalidKey : LookupError
    data object NotFound : LookupError
    data class RateLimited(val retryAfterSeconds: Long?) : LookupError
    data class Unavailable(val retryAfterSeconds: Long?) : LookupError
    data class Rejected(val message: String) : LookupError
    data class Network(val message: String) : LookupError
}

sealed interface LookupOutcome {
    data class Found(val record: RegoRecord, val fromCache: Boolean) : LookupOutcome
    data class Failed(val error: LookupError) : LookupOutcome
}

/** Client for PlateAPI (plateapi.com.au): GET /api/v1/lookup?plate=…&state=…, key in X-API-Key. */
class PlateApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.plateapi.com.au/".toHttpUrl(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(plate: String, state: AuState, apiKey: String): LookupOutcome {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/v1/lookup")
            .addQueryParameter("plate", plate)
            .addQueryParameter("state", state.name)
            // Adds engine code, drivetrain and power where known: these decide the oil and filter.
            .addQueryParameter("detailed", "true")
            .build()
        val request = Request.Builder().url(url).header("X-API-Key", apiKey).build()
        return try {
            client.newCall(request).await().use { parse(it, plate, state) }
        } catch (e: IOException) {
            LookupOutcome.Failed(LookupError.Network(e.message ?: e.javaClass.simpleName))
        }
    }

    /**
     * Opens the DNS + TLS + HTTP/2 connection ahead of the first lookup, so pressing Search costs
     * one round trip instead of three or four. The root path is not a lookup and uses no quota.
     */
    fun preconnect() {
        client.newCall(Request.Builder().url(baseUrl).head().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) = response.close()
        })
    }

    private fun parse(response: Response, plate: String, state: AuState): LookupOutcome {
        val body = response.body.string()
        val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()
        val error = when (response.code) {
            200 -> {
                val parsed = runCatching { json.decodeFromString<LookupBody>(body) }.getOrNull()
                    ?: return failed(LookupError.Rejected("Unexpected response from PlateAPI"))
                if (parsed.success && parsed.vehicle != null) {
                    val record = RegoRecord(
                        plate = plate,
                        state = state,
                        vehicles = listOf(parsed.vehicle) + parsed.alternatives,
                        match = parsed.match,
                        fetchedAt = clock(),
                    )
                    return LookupOutcome.Found(record, fromCache = false)
                }
                when (parsed.code) {
                    "not_found" -> LookupError.NotFound
                    "internal_error", "lookup_failed" -> LookupError.Unavailable(retryAfter)
                    else -> LookupError.Rejected(parsed.error ?: parsed.code ?: "Lookup failed")
                }
            }
            401 -> LookupError.InvalidKey
            429 -> LookupError.RateLimited(retryAfter)
            503 -> LookupError.Unavailable(retryAfter)
            else -> LookupError.Rejected(detail(body) ?: "PlateAPI returned HTTP ${response.code}")
        }
        return failed(error)
    }

    private fun failed(error: LookupError) = LookupOutcome.Failed(error)

    /** Request errors carry {"detail": "…"} (or a list of validation messages). */
    private fun detail(body: String): String? {
        val detail = runCatching { json.decodeFromString<ErrorBody>(body).detail }.getOrNull() ?: return null
        return when (detail) {
            is JsonPrimitive -> detail.content
            is JsonArray -> detail.joinToString("; ") { it.toString() }
            else -> detail.toString()
        }
    }

    @Serializable
    private data class LookupBody(
        val success: Boolean = false,
        val vehicle: Vehicle? = null,
        val alternatives: List<Vehicle> = emptyList(),
        val match: String? = null,
        val code: String? = null,
        val error: String? = null,
    )

    @Serializable
    private data class ErrorBody(val detail: JsonElement? = null)
}

/** Suspends on an OkHttp call and cancels it if the coroutine is cancelled (e.g. a newer search). */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
    })
}
