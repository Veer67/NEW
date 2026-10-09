package com.heading.ai.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.heading.ai.HeadingApp
import com.heading.ai.audio.Mp3AudioPlayer
import com.heading.ai.data.model.GeminiConstants
import com.heading.ai.databinding.ActivitySettingsBinding
import com.heading.ai.network.ElevenLabsApi
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val preferences by lazy { HeadingApp.instance.preferences }
    private var elevenLabsVoices: List<ElevenLabsApi.Voice> = emptyList()
    private var testPlayer: Mp3AudioPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        testPlayer = Mp3AudioPlayer(this, {}, {})

        setupToolbar()
        populateDropdowns()
        loadCurrentSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveSettings() }
    }

    private fun populateDropdowns() {
        binding.spinnerModel.adapter = adapter(GeminiConstants.SUPPORTED_MODELS)
        binding.spinnerVoice.adapter = adapter(GeminiConstants.SUPPORTED_VOICES)
        binding.spinnerVoiceProvider.adapter = adapter(GeminiConstants.SUPPORTED_VOICE_PROVIDERS)
        binding.spinnerElevenLabsVoice.adapter = adapter(listOf("Refresh voices to load"))
        binding.spinnerPersonality.adapter = adapter(GeminiConstants.SUPPORTED_PERSONALITIES)

        binding.spinnerPersonality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.tvPersonalityDesc.text = when (GeminiConstants.SUPPORTED_PERSONALITIES[position]) {
                    GeminiConstants.PERSONALITY_GIRLFRIEND -> "Warm Hinglish • Emotional but natural • Short spoken replies"
                    GeminiConstants.PERSONALITY_PROFESSIONAL -> "Formal English • Precise • No emojis"
                    else -> "Friendly • Helpful • Hinglish or English"
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.spinnerVoiceProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateElevenLabsVisibility()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun <T> adapter(items: List<T>): ArrayAdapter<T> = ArrayAdapter<T>(
        this,
        android.R.layout.simple_spinner_item,
        items
    ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun loadCurrentSettings() {
        binding.etApiKey.setText(preferences.apiKey)
        binding.etElevenLabsApiKey.setText(preferences.elevenLabsApiKey)
        binding.etElevenLabsProxyUrl.setText(
            preferences.elevenLabsProxyUrl.ifBlank { ElevenLabsApi.DEFAULT_PROXY_URL }
        )
        binding.etUserName.setText(preferences.userName)
        select(binding.spinnerModel, GeminiConstants.SUPPORTED_MODELS, preferences.aiModel)
        select(binding.spinnerVoice, GeminiConstants.SUPPORTED_VOICES, preferences.voice)
        select(binding.spinnerVoiceProvider, GeminiConstants.SUPPORTED_VOICE_PROVIDERS, preferences.voiceProvider)
        select(binding.spinnerPersonality, GeminiConstants.SUPPORTED_PERSONALITIES, preferences.personality)
        updateElevenLabsVisibility()
        refreshElevenLabsVoices(showToast = false)
    }

    private fun <T> select(spinner: android.widget.Spinner, values: List<T>, value: T) {
        val index = values.indexOf(value)
        if (index >= 0) spinner.setSelection(index)
    }

    private fun updateElevenLabsVisibility() {
        val enabled = binding.spinnerVoiceProvider.selectedItem?.toString() == GeminiConstants.VOICE_PROVIDER_ELEVENLABS
        val visibility = if (enabled) View.VISIBLE else View.GONE
        binding.tvElevenLabsApiKeyLabel.visibility = visibility
        binding.tilElevenLabsApiKey.visibility = visibility
        binding.btnPasteElevenLabsApiKey.visibility = visibility
        binding.tvElevenLabsProxyLabel.visibility = visibility
        binding.tilElevenLabsProxyUrl.visibility = visibility
        binding.tvElevenLabsVoiceLabel.visibility = visibility
        binding.spinnerElevenLabsVoice.visibility = visibility
        binding.llElevenLabsActions.visibility = visibility
    }

    private fun setupListeners() {
        binding.btnPasteApiKey.setOnClickListener { pasteInto(binding.etApiKey, "Gemini API key pasted") }
        binding.btnPasteElevenLabsApiKey.setOnClickListener {
            pasteInto(binding.etElevenLabsApiKey, "ElevenLabs key pasted")
        }
        binding.btnRefreshElevenLabsVoices.setOnClickListener { refreshElevenLabsVoices(showToast = true) }
        binding.btnTestElevenLabsVoice.setOnClickListener { testElevenLabsVoice() }
    }

    private fun pasteInto(target: android.widget.EditText, message: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString()?.trim().orEmpty() else ""
        if (value.isBlank()) Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        else {
            target.setText(value)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshElevenLabsVoices(showToast: Boolean) {
        val proxyUrl = binding.etElevenLabsProxyUrl.text?.toString()?.trim()
            .orEmpty().ifBlank { ElevenLabsApi.DEFAULT_PROXY_URL }
        val apiKey = binding.etElevenLabsApiKey.text?.toString()?.trim().orEmpty()
        binding.btnRefreshElevenLabsVoices.isEnabled = false
        lifecycleScope.launch {
            ElevenLabsApi.listVoices(proxyUrl, apiKey)
                .onSuccess { voices ->
                    elevenLabsVoices = voices
                    binding.spinnerElevenLabsVoice.adapter = adapter(voices.ifEmpty { listOf(ElevenLabsApi.Voice("", "No voices available")) })
                    val selected = voices.indexOfFirst { it.id == preferences.elevenLabsVoiceId }
                    if (selected >= 0) binding.spinnerElevenLabsVoice.setSelection(selected)
                    if (showToast) Toast.makeText(this@SettingsActivity, "Loaded ${voices.size} ElevenLabs voices", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    if (showToast) Toast.makeText(this@SettingsActivity, "Could not load ElevenLabs voices: ${it.message}", Toast.LENGTH_LONG).show()
                }
            binding.btnRefreshElevenLabsVoices.isEnabled = true
        }
    }

    private fun testElevenLabsVoice() {
        val selected = binding.spinnerElevenLabsVoice.selectedItem as? ElevenLabsApi.Voice
        val voiceId = selected?.id.orEmpty().ifBlank { preferences.elevenLabsVoiceId }
        if (voiceId.isBlank()) {
            Toast.makeText(this, "Load and select an ElevenLabs voice first", Toast.LENGTH_SHORT).show()
            return
        }
        val proxyUrl = binding.etElevenLabsProxyUrl.text?.toString()?.trim()
            .orEmpty().ifBlank { ElevenLabsApi.DEFAULT_PROXY_URL }
        val apiKey = binding.etElevenLabsApiKey.text?.toString()?.trim().orEmpty()
        binding.btnTestElevenLabsVoice.isEnabled = false
        lifecycleScope.launch {
            ElevenLabsApi.synthesize(
                text = "Hello, this is HEADING AI voice test.",
                voiceId = voiceId,
                proxyUrl = proxyUrl,
                apiKey = apiKey
            ).onSuccess { audio ->
                testPlayer?.play(audio)
                Toast.makeText(this@SettingsActivity, "Playing voice test", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@SettingsActivity, "Voice test failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
            binding.btnTestElevenLabsVoice.isEnabled = true
        }
    }

    private fun saveSettings() {
        val selectedElevenVoice = binding.spinnerElevenLabsVoice.selectedItem as? ElevenLabsApi.Voice
        preferences.apiKey = binding.etApiKey.text?.toString()?.trim().orEmpty()
        preferences.elevenLabsApiKey = binding.etElevenLabsApiKey.text?.toString()?.trim().orEmpty()
        preferences.elevenLabsProxyUrl = binding.etElevenLabsProxyUrl.text?.toString()?.trim()
            .orEmpty().ifBlank { ElevenLabsApi.DEFAULT_PROXY_URL }
        preferences.userName = binding.etUserName.text?.toString()?.trim().orEmpty()
        preferences.aiModel = binding.spinnerModel.selectedItem as? String ?: GeminiConstants.DEFAULT_MODEL
        preferences.voice = binding.spinnerVoice.selectedItem as? String ?: "Aoede"
        preferences.voiceProvider = binding.spinnerVoiceProvider.selectedItem as? String ?: GeminiConstants.VOICE_PROVIDER_GEMINI
        preferences.elevenLabsVoiceId = selectedElevenVoice?.id.orEmpty().ifBlank { preferences.elevenLabsVoiceId }
        preferences.personality = binding.spinnerPersonality.selectedItem as? String ?: GeminiConstants.PERSONALITY_ASSISTANT
        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        testPlayer?.release()
        testPlayer = null
        super.onDestroy()
    }
}
