package com.seekerverify.app.rpc

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Fetches SOL/USD price from CoinGecko free API (no key needed).
 * Also fetches SKR/USD via Jupiter price API.
 * Caches results for 5 minutes.
 */
object PriceClient {

    private const val TAG = "SeekerVerify"
    private const val CACHE_MS = 5L * 60 * 1000 // 5 minutes

    private const val COINGECKO_SOL_URL =
        "https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd"

    // Jupiter price API for SKR (free, no auth)
    private const val JUPITER_SKR_URL =
        "https://api.jup.ag/price/v2?ids=SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3"

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Cache
    private val cachedSolPrice = AtomicReference<Double?>(null)
    private val cachedSkrPrice = AtomicReference<Double?>(null)
    private val lastFetchSol = AtomicLong(0L)
    private val lastFetchSkr = AtomicLong(0L)

    data class PriceInfo(
        val solUsd: Double?,
        val skrUsd: Double?
    )

    /**
     * Get current prices. Returns cached values if fresh enough.
     */
    suspend fun getPrices(): PriceInfo {
        val now = System.currentTimeMillis()

        val solPrice = if (now - lastFetchSol.get() < CACHE_MS && cachedSolPrice.get() != null) {
            cachedSolPrice.get()
        } else {
            fetchSolPrice()
        }

        val skrPrice = if (now - lastFetchSkr.get() < CACHE_MS && cachedSkrPrice.get() != null) {
            cachedSkrPrice.get()
        } else {
            fetchSkrPrice()
        }

        return PriceInfo(solPrice, skrPrice)
    }

    private suspend fun fetchSolPrice(): Double? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(COINGECKO_SOL_URL).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val parsed = json.parseToJsonElement(body).jsonObject
            val price = parsed["solana"]?.jsonObject?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()

            if (price != null) {
                cachedSolPrice.set(price)
                lastFetchSol.set(System.currentTimeMillis())
                Log.d(TAG, "SOL price: $$price")
            }
            price
        } catch (e: Exception) {
            Log.e(TAG, "SOL price fetch failed: ${e.message}")
            cachedSolPrice.get() // return stale cache
        }
    }

    private suspend fun fetchSkrPrice(): Double? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(JUPITER_SKR_URL).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val parsed = json.parseToJsonElement(body).jsonObject
            val data = parsed["data"]?.jsonObject
            val skrData = data?.get("SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3")?.jsonObject
            val price = skrData?.get("price")?.jsonPrimitive?.content?.toDoubleOrNull()

            if (price != null) {
                cachedSkrPrice.set(price)
                lastFetchSkr.set(System.currentTimeMillis())
                Log.d(TAG, "SKR price: $$price")
            }
            price
        } catch (e: Exception) {
            Log.e(TAG, "SKR price fetch failed: ${e.message}")
            cachedSkrPrice.get() // return stale cache
        }
    }
}
