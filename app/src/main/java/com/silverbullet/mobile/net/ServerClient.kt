package com.silverbullet.mobile.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Lightweight client for the SilverBullet server API.
 *
 * Mirrors the contract in the upstream source (silverbulletmd/silverbullet 2.10.0):
 *   - fsEndpoint = "/.fs"            (client/spaces/constants.ts)
 *   - GET {base}/.ping with Accept: application/json
 *   - response headers: X-Server-Version, X-Space-Path  (server/src/router.rs)
 */
data class PingResult(
    val ok: Boolean,
    val serverVersion: String? = null,
    val spacePath: String? = null,
    val error: String? = null,
) {
    companion object {
        fun success(version: String?, spacePath: String?) =
            PingResult(ok = true, serverVersion = version, spacePath = spacePath)
        fun failure(error: String) = PingResult(ok = false, error = error)
    }
}

object ServerClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun ping(baseUrl: String, token: String, callback: (PingResult) -> Unit) {
        val url = baseUrl.trim().trimEnd('/')
        val builder = Request.Builder()
            .url("$url/.ping")
            .header("Accept", "application/json")
            .header("X-Sync-Mode", "true")
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        val call = client.newCall(builder.build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(PingResult.failure(e.message ?: "Connection failed"))
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        callback(PingResult.failure("HTTP ${r.code}"))
                        return
                    }
                    val version = r.header("X-Server-Version")
                    val spacePath = r.header("X-Space-Path")
                    callback(PingResult.success(version, spacePath))
                }
            }
        })
    }

    suspend fun pingBlocking(baseUrl: String, token: String): PingResult =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trim().trimEnd('/')
            val builder = Request.Builder()
                .url("$url/.ping")
                .header("Accept", "application/json")
                .header("X-Sync-Mode", "true")
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            try {
                client.newCall(builder.build()).execute().use { r ->
                    if (!r.isSuccessful) {
                        PingResult.failure("HTTP ${r.code}")
                    } else {
                        PingResult.success(
                            r.header("X-Server-Version"),
                            r.header("X-Space-Path"),
                        )
                    }
                }
            } catch (e: IOException) {
                PingResult.failure(e.message ?: "Connection failed")
            }
        }
}