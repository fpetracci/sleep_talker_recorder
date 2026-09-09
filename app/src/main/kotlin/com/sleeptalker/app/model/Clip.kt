package com.sleeptalker.app.model

/**
 * A single saved sleep-talk recording.
 *
 * TODO: this is currently populated with mock data in [com.sleeptalker.app.HomeActivity].
 * Once the Room DB from the README's architecture exists, load these from it instead
 * (one row per "first words_HH:MM.wav" file the recording pipeline saved).
 */
data class Clip(
    val id: Long,
    /** e.g. "first words_03:12" — filename without extension. */
    val label: String,
    val timestamp: String,
    val transcript: String,
    /**
     * Either "asset://<name under assets/>" (used for the bundled tests/samples demo
     * clips) or an absolute filesystem path. Null means no audio is available.
     */
    val filePath: String? = null,
)
