package com.seekerverify.app.rpc

import android.util.Log
import com.seekerverify.app.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized RPC client with rate limiting and provider failover.
 * Supports public Solana RPC and Helius free tier.
 */
object RpcProvider {

    private const val TAG = "SeekerVerify"
    private val JSON_MEDIA = "application/json".toMediaType()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Simple rate limiter: track requests per window
    private val requestCount = AtomicInteger(0)
    private val windowStart = AtomicLong(System.currentTimeMillis())
    private const val RATE_LIMIT_WINDOW_MS = 10_000L // 10 seconds
    private const val MAX_REQUESTS_PER_WINDOW = 5 // conservative for public RPC

    private var requestIdCounter = AtomicInteger(1)

    /**
     * Make a JSON-RPC 2.0 call.
     */
    suspend fun call(
        rpcUrl: String,
        method: String,
        params: JsonElement
    ): Result<JsonElement> = withContext(Dispatchers.IO) {
        try {
            enforceRateLimit()

            val requestId = requestIdCounter.getAndIncrement()
            val body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestId)
                put("method", method)
                put("params", params)
            }.toString()

            Log.d(TAG, "RPC → $method (id=$requestId)")

            val request = Request.Builder()
                .url(rpcUrl)
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty RPC response"))

            if (!response.isSuccessful) {
                Log.e(TAG, "RPC HTTP ${response.code}: $responseBody")
                return@withContext Result.failure(
                    Exception("RPC HTTP ${response.code}: ${responseBody.take(200)}")
                )
            }

            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            // Check for RPC error
            val error = jsonResponse["error"]
            if (error != null) {
                val errorObj = error.jsonObject
                val errorMsg = errorObj["message"]?.jsonPrimitive?.content ?: "Unknown RPC error"
                Log.e(TAG, "RPC error: $errorMsg")
                return@withContext Result.failure(Exception("RPC error: $errorMsg"))
            }

            val result = jsonResponse["result"]
                ?: return@withContext Result.failure(Exception("No result in RPC response"))

            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "RPC call failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Simple sliding window rate limiter.
     */
    private suspend fun enforceRateLimit() {
        val now = System.currentTimeMillis()
        val windowStartTime = windowStart.get()

        if (now - windowStartTime > RATE_LIMIT_WINDOW_MS) {
            // New window
            windowStart.set(now)
            requestCount.set(1)
            return
        }

        val count = requestCount.incrementAndGet()
        if (count > MAX_REQUESTS_PER_WINDOW) {
            val waitMs = RATE_LIMIT_WINDOW_MS - (now - windowStartTime)
            if (waitMs > 0) {
                Log.d(TAG, "Rate limit: waiting ${waitMs}ms")
                kotlinx.coroutines.delay(waitMs)
                windowStart.set(System.currentTimeMillis())
                requestCount.set(1)
            }
        }
    }

    /**
     * Convenience: build params array for common RPC calls.
     */
    fun buildParams(vararg elements: JsonElement): JsonElement {
        return buildJsonArray {
            elements.forEach { add(it) }
        }
    }
}
