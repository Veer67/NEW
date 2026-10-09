package com.heading.ai.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

class Mp3AudioPlayer(
    context: Context,
    private val onPlaybackStateChanged: (Boolean) -> Unit,
    private val onComplete: () -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var mediaPlayer: MediaPlayer? = null
    private var temporaryFile: File? = null

    fun play(audio: ByteArray) {
        stop()
        io.execute {
            try {
                val file = File.createTempFile("heading-elevenlabs-", ".mp3", appContext.cacheDir)
                file.writeBytes(audio)
                temporaryFile = file
                mainHandler.post {
                    try {
                        val player = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            setDataSource(file.absolutePath)
                            setOnPreparedListener {
                                onPlaybackStateChanged(true)
                                start()
                            }
                            setOnCompletionListener {
                                onPlaybackStateChanged(false)
                                cleanup()
                                onComplete()
                            }
                            setOnErrorListener { _, _, _ ->
                                onPlaybackStateChanged(false)
                                cleanup()
                                onComplete()
                                true
                            }
                            prepareAsync()
                        }
                        mediaPlayer = player
                    } catch (error: Exception) {
                        Log.e("Mp3AudioPlayer", "Unable to prepare ElevenLabs audio", error)
                        cleanup()
                        onComplete()
                    }
                }
            } catch (error: Exception) {
                Log.e("Mp3AudioPlayer", "Unable to write ElevenLabs audio", error)
                mainHandler.post { onComplete() }
            }
        }
    }

    fun stop() {
        mainHandler.post {
            mediaPlayer?.let {
                runCatching { if (it.isPlaying) it.stop() }
                it.reset()
                it.release()
            }
            mediaPlayer = null
            cleanup()
            onPlaybackStateChanged(false)
        }
    }

    fun release() {
        stop()
        io.shutdownNow()
    }

    private fun cleanup() {
        temporaryFile?.let { runCatching { it.delete() } }
        temporaryFile = null
        mediaPlayer = null
    }
}
