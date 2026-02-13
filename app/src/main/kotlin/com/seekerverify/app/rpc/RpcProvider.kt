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
    private const val MAX_REQUESTS_PUBLIC = 5 // conservative for public RPC
    private const val MAX_REQUESTS_HELIUS = 25 // Helius free tier allows much more

    private var requestIdCounter = AtomicInteger(1)

    /**
     * Make a JSON-RPC 2.0 call.
     */
    private const val MAX_RETRIES = 3
    private const val RETRY_BASE_DELAY_MS = 2000L

    /**
     * Make a JSON-RPC 2.0 call with automatic retry on 429 (rate limit) errors.
     */
    suspend fun call(
        rpcUrl: String,
        method: String,
        params: JsonElement
    ): Result<JsonElement> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                enforceRateLimit(rpcUrl)

                val requestId = requestIdCounter.getAndIncrement()
                val body = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", requestId)
                    put("method", method)
                    put("params", params)
                }.toString()

                Log.d(TAG, "RPC → $method (id=$requestId${if (attempt > 0) ", retry=$attempt" else ""})")

                val request = Request.Builder()
                    .url(rpcUrl)
                    .post(body.toRequestBody(JSON_MEDIA))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty RPC response"))

                // Retry on 429 (rate limit)
                if (response.code == 429) {
                    val backoffMs = RETRY_BASE_DELAY_MS * (attempt + 1)
                    Log.w(TAG, "RPC 429 for $method, retrying in ${backoffMs}ms (attempt ${attempt + 1}/$MAX_RETRIES)")
                    kotlinx.coroutines.delay(backoffMs)
                    // Reset rate limiter window to avoid immediate re-trigger
                    windowStart.set(System.currentTimeMillis())
                    requestCount.set(0)
                    lastException = Exception("RPC HTTP 429: ${responseBody.take(200)}")
                    continue
                }

                if (!response.isSuccessful) {
                    Log.e(TAG, "RPC HTTP ${response.code}: $responseBody")
                    return@withContext Result.failure(
                        Exception("RPC HTTP ${response.code}: ${responseBody.take(200)}")
                    )
                }

                val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

                // Check for RPC-level 429 error in JSON body
                val error = jsonResponse["error"]
                if (error != null) {
                    val errorObj = error.jsonObject
                    val errorCode = errorObj["code"]?.jsonPrimitive?.content?.toIntOrNull()
                    val errorMsg = errorObj["message"]?.jsonPrimitive?.content ?: "Unknown RPC error"

                    if (errorCode == 429) {
                        val backoffMs = RETRY_BASE_DELAY_MS * (attempt + 1)
                        Log.w(TAG, "RPC JSON 429 for $method, retrying in ${backoffMs}ms")
                        kotlinx.coroutines.delay(backoffMs)
                        windowStart.set(System.currentTimeMillis())
                        requestCount.set(0)
                        lastException = Exception("RPC error 429: $errorMsg")
                        continue
                    }

                    Log.e(TAG, "RPC error: $errorMsg")
                    return@withContext Result.failure(Exception("RPC error: $errorMsg"))
                }

                val result = jsonResponse["result"]
                    ?: return@withContext Result.failure(Exception("No result in RPC response"))

                return@withContext Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "RPC call failed: ${e.message}", e)
                lastException = e
            }
        }

        Result.failure(lastException ?: Exception("RPC call failed after $MAX_RETRIES retries"))
    }

    /**
     * Sliding window rate limiter.
     * Adjusts limits based on whether we're using Helius (generous) or public RPC (strict).
     */
    private suspend fun enforceRateLimit(rpcUrl: String = "") {
        val isHelius = rpcUrl.contains("helius", ignoreCase = true)
        val maxRequests = if (isHelius) MAX_REQUESTS_HELIUS else MAX_REQUESTS_PUBLIC

        val now = System.currentTimeMillis()
        val windowStartTime = windowStart.get()

        if (now - windowStartTime > RATE_LIMIT_WINDOW_MS) {
            // New window
            windowStart.set(now)
            requestCount.set(1)
            return
        }

        val count = requestCount.incrementAndGet()
        if (count > maxRequests) {
            val waitMs = RATE_LIMIT_WINDOW_MS - (now - windowStartTime)
            if (waitMs > 0) {
                Log.d(TAG, "Rate limit: waiting ${waitMs}ms (${if (isHelius) "helius" else "public"})")
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
