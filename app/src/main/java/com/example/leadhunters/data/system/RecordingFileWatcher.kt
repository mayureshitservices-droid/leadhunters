package com.example.leadhunters.data.system

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.util.Locale

class RecordingFileWatcher(
    private val directoriesToWatch: List<String>
) {
    private val observers = mutableListOf<FileObserver>()
    private val discoveredFiles = mutableSetOf<String>()
    @Volatile private var active = false

    var onFileDiscovered: ((String) -> Unit)? = null

    fun startWatching() {
        if (active) return
        synchronized(this) {
            if (active) return
            active = true
            discoveredFiles.clear()
            observers.clear()
        }

        val newObservers = mutableListOf<FileObserver>()
        for (dirPath in directoriesToWatch) {
            val dir = File(dirPath)
            if (!dir.exists() || !dir.isDirectory) continue
            try {
                val observer = RecordingsObserver(dirPath)
                observer.startWatching()
                newObservers.add(observer)
                Log.d("RecordingFileWatcher", "Watching: $dirPath")
            } catch (e: Exception) {
                Log.e("RecordingFileWatcher", "Failed watching $dirPath", e)
            }
        }
        synchronized(this) {
            observers.addAll(newObservers)
        }
        Log.i("RecordingFileWatcher", "Watching ${newObservers.size} directories for new recordings")
    }

    fun stopWatching() {
        active = false
        val obsCopy: List<FileObserver>
        synchronized(this) {
            obsCopy = observers.toList()
            observers.clear()
            discoveredFiles.clear()
        }
        for (obs in obsCopy) {
            try {
                obs.stopWatching()
            } catch (_: Exception) {}
        }
        Log.i("RecordingFileWatcher", "Stopped watcher, cleared ${obsCopy.size} observers")
    }

    fun getDiscoveredFiles(): Set<String> = synchronized(this) { discoveredFiles.toSet() }

    private inner class RecordingsObserver(
        private val dirPath: String
    ) : FileObserver(dirPath, CREATE or CLOSE_WRITE or MOVED_TO) {
        override fun onEvent(event: Int, fileName: String?) {
            if (!active || fileName == null || fileName.startsWith(".")) return
            val lower = fileName.lowercase(Locale.ROOT)
            if (!isAudioExtension(lower)) return
            if (event and CREATE != 0 && lower.endsWith(".tmp")) return

            val fullPath = "$dirPath/$fileName"
            val file = File(fullPath)
            if (!file.exists() || file.length() == 0L) return

            val added = synchronized(this@RecordingFileWatcher) { discoveredFiles.add(fullPath) }
            if (added) {
                onFileDiscovered?.invoke(fullPath)
                Log.i("RecordingFileWatcher", "Discovered recording: $fullPath")
            }
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "aac", "m4a", "wav", "amr", "3gp", "ogg", "opus")

        fun isAudioExtension(name: String): Boolean {
            return AUDIO_EXTENSIONS.any { name.endsWith(".$it") }
        }

        fun resolveAbsolutePaths(rootPath: String, relativePaths: List<String>): List<String> {
            return relativePaths.map { "$rootPath$it" }
        }
    }
}
