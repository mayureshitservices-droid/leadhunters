package com.example.leadhunters.ui.logs

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
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

        if (_state.value.currentLogId != logId) {
            stop()
        }

        try {
            if (mediaPlayer == null) {
                val possibleUris = buildList {
                    val mediaStoreUri = resolveMediaStoreUri(filePath)
                    if (mediaStoreUri != null) add(mediaStoreUri)
                    val file = File(filePath)
                    if (file.exists()) add(Uri.fromFile(file))
                }

                var newPlayer: MediaPlayer? = null
                var usedUri: Uri? = null
                for (uri in possibleUris) {
                    newPlayer = MediaPlayer.create(context, uri)
                    if (newPlayer != null) {
                        usedUri = uri
                        Log.i("CallPlaybackManager", "Created player with: $uri")
                        break
                    }
                    Log.w("CallPlaybackManager", "MediaPlayer.create failed for: $uri")
                }

                if (newPlayer == null) {
                    Log.e("CallPlaybackManager", "All URI strategies failed for $filePath")
                    _state.value = _state.value.copy(error = "Unsupported audio format or file corrupted")
                    return
                }

                mediaPlayer = newPlayer.apply {
                    setOnCompletionListener {
                        stop()
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("CallPlaybackManager", "MediaPlayer error: what=$what extra=$extra")
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
            Log.i("CallPlaybackManager", "Playback started for log $logId: $filePath")
        } catch (e: Exception) {
            Log.e("CallPlaybackManager", "Error during playback initialization", e)
            _state.value = _state.value.copy(error = "Could not initialize player: ${e.message}")
            stop()
        }
    }

    private fun resolveMediaStoreUri(filePath: String): Uri? {
        return try {
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(filePath)
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                } else null
            }
        } catch (e: Exception) {
            Log.e("CallPlaybackManager", "Failed to resolve MediaStore URI", e)
            null
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
