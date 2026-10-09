package com.heading.ai.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.heading.ai.data.model.ChatTurn
import com.heading.ai.data.model.GeminiConstants

/**
 * Local Heading AI settings. Sensitive values are encrypted with an Android Keystore-backed key.
 * This keeps user-entered provider credentials out of plain-text SharedPreferences and Logcat.
 */
class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "heading_secure_prefs"
        private const val KEY_API_KEY = "key_api_key"
        private const val KEY_MODEL = "key_model"
        private const val KEY_VOICE = "key_voice"
        private const val KEY_VOICE_PROVIDER = "key_voice_provider"
        private const val KEY_ELEVENLABS_API_KEY = "key_elevenlabs_api_key"
        private const val KEY_ELEVENLABS_VOICE_ID = "key_elevenlabs_voice_id"
        private const val KEY_ELEVENLABS_PROXY_URL = "key_elevenlabs_proxy_url"
        private const val KEY_PERSONALITY = "key_personality"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_MIC_MUTED = "key_mic_muted"
        private const val KEY_CHAT_HISTORY = "key_chat_history"
    }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var aiModel: String
        get() {
            val stored = prefs.getString(KEY_MODEL, GeminiConstants.DEFAULT_MODEL)
                ?: GeminiConstants.DEFAULT_MODEL
            return if (GeminiConstants.SUPPORTED_MODELS.contains(stored)) {
                stored
            } else {
                GeminiConstants.DEFAULT_MODEL
            }
        }
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var voice: String
        get() = prefs.getString(KEY_VOICE, "Aoede") ?: "Aoede"
        set(value) = prefs.edit().putString(KEY_VOICE, value).apply()

    var voiceProvider: String
        get() = prefs.getString(KEY_VOICE_PROVIDER, GeminiConstants.VOICE_PROVIDER_GEMINI)
            ?: GeminiConstants.VOICE_PROVIDER_GEMINI
        set(value) = prefs.edit().putString(KEY_VOICE_PROVIDER, value).apply()

    var elevenLabsApiKey: String
        get() = prefs.getString(KEY_ELEVENLABS_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ELEVENLABS_API_KEY, value.trim()).apply()

    var elevenLabsVoiceId: String
        get() = prefs.getString(KEY_ELEVENLABS_VOICE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ELEVENLABS_VOICE_ID, value.trim()).apply()

    var elevenLabsProxyUrl: String
        get() = prefs.getString(KEY_ELEVENLABS_PROXY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ELEVENLABS_PROXY_URL, value.trim().trimEnd('/')).apply()

    var personality: String
        get() = prefs.getString(KEY_PERSONALITY, GeminiConstants.PERSONALITY_ASSISTANT)
            ?: GeminiConstants.PERSONALITY_ASSISTANT
        set(value) = prefs.edit().putString(KEY_PERSONALITY, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value.trim()).apply()

    var isMicMuted: Boolean
        get() = prefs.getBoolean(KEY_MIC_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_MIC_MUTED, value).apply()

    fun loadChatHistory(): List<ChatTurn> {
        val json = prefs.getString(KEY_CHAT_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ChatTurn>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveChatHistory(history: List<ChatTurn>) {
        prefs.edit().putString(KEY_CHAT_HISTORY, gson.toJson(history)).apply()
    }

    fun clearChatHistory() {
        prefs.edit().remove(KEY_CHAT_HISTORY).apply()
    }
}
