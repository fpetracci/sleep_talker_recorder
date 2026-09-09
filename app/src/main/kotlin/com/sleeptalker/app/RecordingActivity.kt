package com.sleeptalker.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sleeptalker.app.databinding.ActivityRecordingBinding
import com.sleeptalker.app.recording.Phase
import com.sleeptalker.app.recording.RecordingService
import com.sleeptalker.app.recording.RecordingUiState

/**
 * Recording screen: binds to [RecordingService] (already started as a foreground
 * service by [HomeActivity]) and mirrors its [RecordingUiState] — phase, live
 * waveform, elapsed time.
 */
class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private var service: RecordingService? = null
    private val liveSamples = ArrayDeque<Float>()
    private var lastClipsSaved = 0

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as RecordingService.LocalBinder).getService()
            service?.uiState?.observe(this@RecordingActivity) { state -> render(state) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonStop.setOnClickListener {
            stopService(Intent(this, RecordingService::class.java))
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, RecordingService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
    }

    private fun render(state: RecordingUiState) {
        binding.textStatus.text = when (state.phase) {
            Phase.LOADING_MODEL -> getString(R.string.recording_status_loading)
            Phase.SETTLING -> getString(
                R.string.recording_status_settling_countdown,
                formatMinutesSeconds(state.settlingSecondsLeft),
            )
            Phase.MONITORING -> getString(R.string.recording_status_monitoring)
            Phase.RECORDING -> getString(R.string.recording_status_recording)
        }
        binding.textElapsed.text = formatMinutesSeconds(state.elapsedSeconds)
        binding.textClipsSaved.text = getString(R.string.recording_clips_saved, state.clipsSavedThisSession)

        // No sound/vibration here on purpose — the person using this screen is
        // asleep. A silent toast (only relevant while the screen happens to be
        // open) plus the always-updated counter above is the feedback.
        if (state.clipsSavedThisSession > lastClipsSaved) {
            Toast.makeText(this, R.string.recording_clip_saved_toast, Toast.LENGTH_SHORT).show()
        }
        lastClipsSaved = state.clipsSavedThisSession

        liveSamples.addLast(state.liveAmplitude.coerceIn(0f, 1f))
        while (liveSamples.size > LIVE_SAMPLE_WINDOW) liveSamples.removeFirst()
        binding.waveformLive.samples = liveSamples.toList()
    }

    private fun formatMinutesSeconds(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%02d:%02d".format(m, s)
    }

    companion object {
        private const val LIVE_SAMPLE_WINDOW = 40
    }
}
