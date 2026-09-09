package com.sleeptalker.app.recording

enum class Phase { LOADING_MODEL, SETTLING, MONITORING, RECORDING }

data class RecordingUiState(
    val phase: Phase = Phase.LOADING_MODEL,
    val elapsedSeconds: Int = 0,
    val settlingSecondsLeft: Int = 0,
    val liveAmplitude: Float = 0f,
    val clipsSavedThisSession: Int = 0,
)
