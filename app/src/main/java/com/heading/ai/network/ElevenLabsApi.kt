package com.heading.ai.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs voice API for HEADING. The default path uses the managed proxy;
 * a user-entered key enables the optional direct path.
 */
object ElevenLabsApi {
    // Temporary managed preview endpoint. Replace with the published proxy URL before release.
    const val DEFAULT_PROXY_URL = "https://8328-ikzke01ddz0wygyzh6mkh-db55576d.sg2.manus.computer"
    private const val DIRECT_BASE_URL = "https://api.elevenlabs.io"
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    data class Voice(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("category") val category: String? = null
    ) {
        override fun toString(): String = name
    }

    suspend fun listVoices(proxyUrl: String, apiKey: String): Result<List<Voice>> = withContext(Dispatchers.IO) {
        runCatching {
            val direct = apiKey.isNotBlank()
            val base = if (direct) DIRECT_BASE_URL else proxyUrl.trim().trimEnd('/')
            val requestBuilder = Request.Builder().url("$base/${if (direct) "v2/voices" else "v1/voices"}")
                .get().header("Accept", "application/json")
            if (direct) requestBuilder.header("xi-api-key", apiKey)
            client.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Voice list failed (${response.code})")
                val payload = gson.fromJson(body, VoiceListResponse::class.java)
                payload.voices.orEmpty().filter { it.id.isNotBlank() }
            }
        }
    }

    suspend fun synthesize(
        text: String,
        voiceId: String,
        proxyUrl: String,
        apiKey: String,
        modelId: String = "eleven_multilingual_v2"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            require(text.trim().isNotEmpty()) { "Nothing to speak" }
            require(text.length <= 4000) { "Speech text is limited to 4,000 characters" }
            require(voiceId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid ElevenLabs voice ID" }
            val direct = apiKey.isNotBlank()
            val base = if (direct) DIRECT_BASE_URL else proxyUrl.trim().trimEnd('/')
            val path = if (direct) {
                "/v1/text-to-speech/$voiceId?output_format=mp3_44100_128"
            } else {
                "/v1/speech"
            }
            val payload = if (direct) {
                gson.toJson(mapOf(
                    "text" to text.trim(),
                    "model_id" to modelId,
                    "voice_settings" to mapOf("stability" to 0.5, "similarity_boost" to 0.75)
                ))
            } else {
                gson.toJson(mapOf("text" to text.trim(), "voiceId" to voiceId, "modelId" to modelId))
            }
            val requestBuilder = Request.Builder()
                .url("$base$path")
                .post(payload.toRequestBody(jsonType))
                .header("Accept", "audio/mpeg")
            if (direct) requestBuilder.header("xi-api-key", apiKey)
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) error("Speech request failed (${response.code})")
                response.body?.bytes() ?: error("ElevenLabs returned empty audio")
            }
        }
    }

    private data class VoiceListResponse(@SerializedName("voices") val voices: List<Voice>?)
}
