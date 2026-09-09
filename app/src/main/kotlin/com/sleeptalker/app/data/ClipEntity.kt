package com.sleeptalker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved sleep-talk recording, as persisted by [SleepTalkerDatabase]. */
@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val timestamp: String,
    val transcript: String,
    /** "asset://<name under assets/>" for bundled demo clips, else an absolute path. */
    val filePath: String?,
    val recordedAtMillis: Long,
)
