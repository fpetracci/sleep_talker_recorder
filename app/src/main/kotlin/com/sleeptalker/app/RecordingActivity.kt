package com.sleeptalker.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.sleeptalker.app.databinding.ActivityRecordingBinding
import kotlin.random.Random

/**
 * Recording screen: shows the session's current phase, a live waveform, and elapsed time.
 *
 * TODO: this whole screen currently drives itself off a [Handler]-based simulation
 * (fake amplitude samples, a fixed settling timer) because the real Foreground
 * Service + AudioRecord pipeline described in the README doesn't exist yet. Once it
 * does, replace [pushWaveformSample]/[advanceOneSecond] with a listener/observer on
 * that service's state (settling countdown, [com.sleeptalker.app.AmplitudeAnalyzer]
 * RMS values, and settling → monitoring → recording transitions) instead of
 * generating them here.
 */
class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random(System.currentTimeMillis())

    private var waitSecondsLeft = 0
    private var elapsedSeconds = 0
    private var phase = Phase.SETTLING
    private var recordingPhaseTicksLeft = 0
    private val liveSamples = ArrayDeque<Float>()

    private enum class Phase { SETTLING, MONITORING, RECORDING }

    /** Drives the waveform — runs often, so it looks "live". */
    private val waveformTick = object : Runnable {
        override fun run() {
            pushWaveformSample()
            handler.postDelayed(this, WAVEFORM_TICK_MILLIS)
        }
    }

    /** Drives the clock and phase transitions — runs once a second. */
    private val clockTick = object : Runnable {
        override fun run() {
            advanceOneSecond()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        waitSecondsLeft = intent.getIntExtra(EXTRA_WAIT_MINUTES, DEFAULT_WAIT_MINUTES) * 60
        updateStatusText()
        binding.buttonStop.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        handler.post(waveformTick)
        handler.post(clockTick)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(waveformTick)
        handler.removeCallbacks(clockTick)
    }

    private fun advanceOneSecond() {
        elapsedSeconds++
        binding.textElapsed.text = formatMinutesSeconds(elapsedSeconds)

        when (phase) {
            Phase.SETTLING -> {
                waitSecondsLeft--
                if (waitSecondsLeft <= 0) phase = Phase.MONITORING
            }
            Phase.MONITORING -> {
                // Occasionally simulate a threshold crossing to demo the RECORDING phase.
                if (random.nextInt(100) < 20) {
                    phase = Phase.RECORDING
                    recordingPhaseTicksLeft = 3 + random.nextInt(4)
                }
            }
            Phase.RECORDING -> {
                recordingPhaseTicksLeft--
                if (recordingPhaseTicksLeft <= 0) phase = Phase.MONITORING
            }
        }
        updateStatusText()
    }

    private fun updateStatusText() {
        binding.textStatus.text = when (phase) {
            Phase.SETTLING -> getString(
                R.string.recording_status_settling_countdown,
                formatMinutesSeconds(waitSecondsLeft),
            )
            Phase.MONITORING -> getString(R.string.recording_status_monitoring)
            Phase.RECORDING -> getString(R.string.recording_status_recording)
        }
    }

    private fun pushWaveformSample() {
        val amplitude = when (phase) {
            Phase.SETTLING, Phase.MONITORING -> random.nextFloat() * 0.15f // quiet/ambient
            Phase.RECORDING -> 0.4f + random.nextFloat() * 0.6f // above-threshold
        }
        liveSamples.addLast(amplitude)
        while (liveSamples.size > LIVE_SAMPLE_WINDOW) liveSamples.removeFirst()
        binding.waveformLive.samples = liveSamples.toList()
    }

    private fun formatMinutesSeconds(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%02d:%02d".format(m, s)
    }

    companion object {
        const val DEFAULT_WAIT_MINUTES = 15
        private const val EXTRA_WAIT_MINUTES = "extra_wait_minutes"
        private const val WAVEFORM_TICK_MILLIS = 150L
        private const val LIVE_SAMPLE_WINDOW = 40

        fun newIntent(context: Context, waitMinutes: Int): Intent =
            Intent(context, RecordingActivity::class.java)
                .putExtra(EXTRA_WAIT_MINUTES, waitMinutes)
    }
}
