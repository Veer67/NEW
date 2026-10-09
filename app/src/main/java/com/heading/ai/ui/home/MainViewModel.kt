package com.heading.ai.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heading.ai.HeadingApp
import com.heading.ai.audio.AudioPlayer
import com.heading.ai.audio.AudioRecorder
import com.heading.ai.audio.Mp3AudioPlayer
import com.heading.ai.data.model.ConversationState
import com.heading.ai.data.model.GeminiConstants
import com.heading.ai.data.preferences.AppPreferences
import com.heading.ai.data.repository.ChatRepository
import com.heading.ai.network.ElevenLabsApi
import com.heading.ai.network.GeminiLiveWebSocket
import com.heading.ai.util.PromptGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences: AppPreferences = (application as HeadingApp).preferences
    private val chatRepository: ChatRepository = (application as HeadingApp).chatRepository
    private val _isSessionOn = MutableStateFlow(false)
    val isSessionOn: StateFlow<Boolean> = _isSessionOn.asStateFlow()
    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()
    private val _isMicMuted = MutableStateFlow(preferences.isMicMuted)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()
    private val _connectionStatus = MutableStateFlow("READY")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    private val _liveTime = MutableStateFlow("")
    val liveTime: StateFlow<String> = _liveTime.asStateFlow()
    private val _personalityName = MutableStateFlow(preferences.personality)
    val personalityName: StateFlow<String> = _personalityName.asStateFlow()
    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    private var audioRecorder: AudioRecorder? = null
    private var audioPlayer: AudioPlayer? = null
    private var elevenLabsPlayer: Mp3AudioPlayer? = null
    private var liveWebSocket: GeminiLiveWebSocket? = null
    private var timeClockJob: Job? = null
    private val currentTurnAssistantText = StringBuilder()
    private var lastUserSpeechDetectedTime = 0L

    init { startTimeClock() }

    private fun startTimeClock() {
        timeClockJob = viewModelScope.launch(Dispatchers.Default) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (isActive) {
                _liveTime.value = sdf.format(Date())
                delay(1000)
            }
        }
    }

    fun refreshSettings() {
        _personalityName.value = preferences.personality
        _isMicMuted.value = preferences.isMicMuted
        audioRecorder?.setMuted(preferences.isMicMuted)
    }

    fun toggleSession() { if (_isSessionOn.value) stopSession() else startSession() }

    private fun startSession() {
        val apiKey = preferences.apiKey.trim()
        val useElevenLabs = preferences.voiceProvider == GeminiConstants.VOICE_PROVIDER_ELEVENLABS
        if (apiKey.isBlank()) {
            emitEvent("Please configure your Gemini API Key in Settings first.")
            return
        }
        if (useElevenLabs && preferences.elevenLabsVoiceId.isBlank()) {
            emitEvent("Select an ElevenLabs voice in Settings first.")
            return
        }

        _isSessionOn.value = true
        _conversationState.value = ConversationState.IDLE
        currentTurnAssistantText.clear()

        if (useElevenLabs) {
            elevenLabsPlayer = Mp3AudioPlayer(
                getApplication(),
                onPlaybackStateChanged = { playing ->
                    _conversationState.value = if (playing) ConversationState.SPEAKING else ConversationState.IDLE
                    if (!playing) _audioLevel.value = 0f
                },
                onComplete = { finalizeTurn() }
            )
        } else {
            audioPlayer = AudioPlayer(
                onPlaybackStateChanged = { isPlaying ->
                    if (isPlaying) _conversationState.value = ConversationState.SPEAKING
                    else {
                        finalizeTurn()
                        if (_isSessionOn.value) _conversationState.value = ConversationState.IDLE
                    }
                },
                onPlaybackAmplitude = { amp ->
                    if (_conversationState.value == ConversationState.SPEAKING) _audioLevel.value = amp
                }
            ).apply { start() }
        }

        val systemPrompt = PromptGenerator.generateSystemPrompt(
            personality = preferences.personality,
            userName = preferences.userName
        )
        liveWebSocket = GeminiLiveWebSocket(
            apiKey = apiKey,
            model = preferences.aiModel,
            voiceName = preferences.voice,
            systemPrompt = systemPrompt,
            useNativeAudio = !useElevenLabs,
            listener = object : GeminiLiveWebSocket.Listener {
                override fun onConnectionStateChanged(status: String) {
                    _connectionStatus.value = status
                    if (status == "LIVE" && _conversationState.value == ConversationState.IDLE) {
                        _conversationState.value = ConversationState.LISTENING
                    }
                }
                override fun onAudioDataReceived(pcmData: ByteArray) {
                    if (!useElevenLabs) {
                        _conversationState.value = ConversationState.SPEAKING
                        audioPlayer?.enqueueAudio(pcmData)
                    }
                }
                override fun onAssistantTextReceived(textChunk: String) { currentTurnAssistantText.append(textChunk) }
                override fun onInterrupted() {
                    if (useElevenLabs) elevenLabsPlayer?.stop() else audioPlayer?.flush()
                    currentTurnAssistantText.clear()
                    _conversationState.value = ConversationState.LISTENING
                }
                override fun onTurnCompleted() {
                    if (useElevenLabs) synthesizeCurrentReply()
                }
                override fun onError(message: String) { emitEvent(message) }
            }
        ).apply { connect(viewModelScope) }

        audioRecorder = AudioRecorder { chunk, amplitude ->
            if (_isSessionOn.value) {
                liveWebSocket?.sendAudioChunk(chunk)
                if (amplitude > 0.08f) {
                    lastUserSpeechDetectedTime = System.currentTimeMillis()
                    if (_conversationState.value != ConversationState.SPEAKING) {
                        _conversationState.value = ConversationState.LISTENING
                        _audioLevel.value = amplitude
                    }
                } else if (_conversationState.value == ConversationState.LISTENING) {
                    _audioLevel.value = amplitude
                    val silentDuration = System.currentTimeMillis() - lastUserSpeechDetectedTime
                    if (silentDuration > 700 && lastUserSpeechDetectedTime > 0) {
                        liveWebSocket?.sendAudioStreamEnd()
                        _conversationState.value = ConversationState.THINKING
                        lastUserSpeechDetectedTime = 0L
                    }
                }
            }
        }.apply {
            setMuted(preferences.isMicMuted)
            start(viewModelScope)
        }
    }

    private fun synthesizeCurrentReply() {
        val reply = currentTurnAssistantText.toString().trim()
        if (reply.isBlank()) return
        _conversationState.value = ConversationState.THINKING
        viewModelScope.launch {
            ElevenLabsApi.synthesize(
                text = reply,
                voiceId = preferences.elevenLabsVoiceId,
                proxyUrl = preferences.elevenLabsProxyUrl.ifBlank { ElevenLabsApi.DEFAULT_PROXY_URL },
                apiKey = preferences.elevenLabsApiKey
            ).onSuccess { audio ->
                if (_isSessionOn.value) elevenLabsPlayer?.play(audio)
            }.onFailure {
                emitEvent("ElevenLabs voice failed: ${it.message}. Gemini voice remains available in Settings.")
                finalizeTurn()
                if (_isSessionOn.value) _conversationState.value = ConversationState.LISTENING
            }
        }
    }

    private fun finalizeTurn() {
        val reply = currentTurnAssistantText.toString().trim()
        if (reply.isNotEmpty()) {
            chatRepository.addTurn(userText = "Spoken user query", headingText = reply)
            currentTurnAssistantText.clear()
        }
    }

    private fun stopSession() {
        _isSessionOn.value = false
        finalizeTurn()
        audioRecorder?.stop()
        audioRecorder = null
        audioPlayer?.flush()
        audioPlayer?.release()
        audioPlayer = null
        elevenLabsPlayer?.release()
        elevenLabsPlayer = null
        liveWebSocket?.disconnect()
        liveWebSocket = null
        _conversationState.value = ConversationState.IDLE
        _connectionStatus.value = "READY"
        _audioLevel.value = 0f
    }

    private fun emitEvent(message: String) {
        viewModelScope.launch { _eventFlow.emit(message) }
    }

    fun toggleMicMute() {
        val newMuted = !_isMicMuted.value
        _isMicMuted.value = newMuted
        preferences.isMicMuted = newMuted
        audioRecorder?.setMuted(newMuted)
        emitEvent(if (newMuted) "Microphone Muted" else "Microphone Unmuted")
    }

    override fun onCleared() {
        super.onCleared()
        timeClockJob?.cancel()
        stopSession()
    }
}
