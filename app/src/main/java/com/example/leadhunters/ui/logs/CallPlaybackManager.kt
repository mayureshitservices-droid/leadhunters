package com.example.leadhunters.ui.logs

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val currentLogId: Long? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val error: String? = null
)

@Singleton
class CallPlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            try {
                if (player.isPlaying) {
                    _state.value = _state.value.copy(
                        currentPosition = player.currentPosition,
                        duration = player.duration
                    )
                    handler.postDelayed(this, 500)
                }
            } catch (e: Exception) {
                // Player might have been released just now
                stop()
            }
        }
    }

    fun play(logId: Long, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            _state.value = _state.value.copy(error = "Recording file not found at $filePath")
            return
        }

        // Stop current if playing something else
        if (_state.value.currentLogId != logId) {
            stop()
        }

        try {
            if (mediaPlayer == null) {
                val newPlayer = MediaPlayer.create(context, Uri.fromFile(file))
                if (newPlayer == null) {
                    _state.value = _state.value.copy(error = "Unsupported audio format or file corrupted")
                    com.example.leadhunters.util.CrashReporter.log("ERROR: MediaPlayer.create returned null for $filePath")
                    return
                }
                
                mediaPlayer = newPlayer.apply {
                    setOnCompletionListener {
                        stop()
                    }
                    setOnErrorListener { _, what, extra ->
                        com.example.leadhunters.util.CrashReporter.log("ERROR: MediaPlayer error: $what, $extra")
                        _state.value = _state.value.copy(error = "Playback error occurred")
                        stop()
                        true
                    }
                }
            }
            
            mediaPlayer?.start()
            _state.value = _state.value.copy(
                currentLogId = logId,
                isPlaying = true,
                duration = mediaPlayer?.duration ?: 0
            )
            handler.post(progressUpdater)
            com.example.leadhunters.util.CrashReporter.log("Playback started for log $logId")
        } catch (e: Exception) {
            com.example.leadhunters.util.CrashReporter.logError(e, "Error during playback initialization")
            _state.value = _state.value.copy(error = "Could not initialize player: ${e.message}")
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        _state.value = _state.value.copy(isPlaying = false)
        handler.removeCallbacks(progressUpdater)
    }

    fun stop() {
        handler.removeCallbacks(progressUpdater)
        mediaPlayer?.release()
        mediaPlayer = null
        _state.value = PlaybackState()
    }

    fun togglePlayback(logId: Long, filePath: String) {
        if (_state.value.currentLogId == logId && _state.value.isPlaying) {
            pause()
        } else {
            play(logId, filePath)
        }
    }
    
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
