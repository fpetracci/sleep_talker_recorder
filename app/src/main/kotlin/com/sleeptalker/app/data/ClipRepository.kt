package com.sleeptalker.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.sleeptalker.app.model.Clip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Single source of truth for [Clip]s shown in the UI, backed by [SleepTalkerDatabase]. */
class ClipRepository(context: Context) {

    private val dao = SleepTalkerDatabase.get(context).clipDao()

    val clips: LiveData<List<Clip>> = dao.observeAll().map { entities -> entities.map { it.toClip() } }

    /** Must be called off the main thread (Room forbids DB writes on it). */
    fun insert(label: String, timestamp: String, transcript: String, filePath: String) {
        dao.insert(
            ClipEntity(
                label = label,
                timestamp = timestamp,
                transcript = transcript,
                filePath = filePath,
                recordedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    companion object {
        private val labelTimeFormat = SimpleDateFormat("HH:mm", Locale.US)
        private const val LABEL_WORD_COUNT = 4

        /** e.g. "ciao sono francesco · 13:11" — the actual words spoken, not a placeholder. */
        fun labelFor(transcript: String, atMillis: Long): String =
            "${firstWords(transcript)} · ${labelTimeFormat.format(Date(atMillis))}"

        fun timestampFor(atMillis: Long): String = labelTimeFormat.format(Date(atMillis))

        /** Filesystem-safe slug of the first words, for the saved .wav's filename. */
        fun fileSlug(transcript: String): String =
            firstWords(transcript)
                .lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .ifBlank { "clip" }

        private fun firstWords(transcript: String): String =
            transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(LABEL_WORD_COUNT).joinToString(" ")
    }
}

private fun ClipEntity.toClip() = Clip(
    id = id,
    label = label,
    timestamp = timestamp,
    transcript = transcript,
    filePath = filePath,
)
