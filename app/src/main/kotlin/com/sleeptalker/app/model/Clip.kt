package com.sleeptalker.app.model

/** A single saved sleep-talk recording, as loaded from [com.sleeptalker.app.data.ClipRepository]. */
data class Clip(
    val id: Long,
    /** The words actually spoken plus a timestamp, e.g. "ciao sono francesco · 13:11". */
    val label: String,
    val timestamp: String,
    val transcript: String,
    /** Absolute filesystem path to the saved .wav. Null means no audio is available. */
    val filePath: String? = null,
)
