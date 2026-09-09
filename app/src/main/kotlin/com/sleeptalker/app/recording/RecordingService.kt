package com.sleeptalker.app.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import com.sleeptalker.app.HomeActivity
import com.sleeptalker.app.R
import com.sleeptalker.app.AmplitudeAnalyzer
import com.sleeptalker.app.RingBuffer
import com.sleeptalker.app.audio.VoskModelProvider
import com.sleeptalker.app.audio.WavWriter
import com.sleeptalker.app.data.ClipRepository
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Foreground service running the real settling → monitoring → recording state
 * machine: calibrates an ambient-noise threshold during settling, watches
 * amplitude in monitoring, captures audio (with a pre-buffer so word onsets aren't
 * clipped) once that threshold is crossed, transcribes it with Vosk, and saves it
 * only if words were found — matching the README's architecture.
 *
 * [LifecycleService] (not a plain Service) so the bundled LiveData observation APIs
 * work without extra wiring; UI reads [uiState] via [LocalBinder] while bound.
 */
class RecordingService : LifecycleService() {

    private val binder = LocalBinder()
    private val _uiState = MutableLiveData(RecordingUiState())
    val uiState: MutableLiveData<RecordingUiState> get() = _uiState

    private lateinit var repository: ClipRepository
    @Volatile private var stopRequested = false
    private var workerThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        repository = ClipRepository(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val waitMinutes = intent?.getIntExtra(EXTRA_WAIT_MINUTES, DEFAULT_WAIT_MINUTES) ?: DEFAULT_WAIT_MINUTES

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.recording_status_loading)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        val wakeLockManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = wakeLockManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepTalker::Recording").apply {
            acquire(MAX_SESSION_MILLIS)
        }

        stopRequested = false
        workerThread = Thread { runStateMachine(waitMinutes) }.apply { start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopRequested = true
        workerThread?.join(THREAD_JOIN_TIMEOUT_MILLIS)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    // ---- State machine (runs entirely on workerThread) ----------------------------

    private fun runStateMachine(waitMinutes: Int) {
        postState { it.copy(phase = Phase.LOADING_MODEL) }
        val model = VoskModelProvider.load(this)

        val minBufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferBytes <= 0) {
            stopSelf()
            return
        }
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
            maxOf(minBufferBytes, CHUNK_BYTES) * 2,
        )

        try {
            audioRecord.startRecording()
            val threshold = runSettling(audioRecord, waitMinutes)
            // Elapsed time (shown on the recording screen) counts from the end of
            // settling, not from session start — settling can be many minutes long
            // and isn't "recording" in the sense the timer communicates.
            val monitoringStart = System.currentTimeMillis()
            var clipsSaved = 0
            while (!stopRequested) {
                clipsSaved += runMonitoringUntilRecordingSaved(audioRecord, threshold, model, monitoringStart, clipsSaved)
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
            model.close()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Reads ambient RMS for [waitMinutes] and returns the crossing threshold. */
    private fun runSettling(audioRecord: AudioRecord, waitMinutes: Int): Float {
        val waitSeconds = waitMinutes * 60
        val chunk = ByteArray(CHUNK_BYTES)
        var rmsSum = 0.0
        var rmsCount = 0
        val settlingEndAt = System.currentTimeMillis() + waitSeconds * 1000L

        while (!stopRequested && System.currentTimeMillis() < settlingEndAt) {
            val read = audioRecord.read(chunk, 0, chunk.size)
            if (read <= 0) continue
            val rms = AmplitudeAnalyzer.rms(chunk)
            rmsSum += rms
            rmsCount++
            val secondsLeft = ((settlingEndAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            postState {
                it.copy(
                    phase = Phase.SETTLING,
                    settlingSecondsLeft = secondsLeft,
                    liveAmplitude = rms,
                    elapsedSeconds = 0, // timer starts once settling ends, not at session start
                )
            }
        }

        val baseline = if (rmsCount > 0) (rmsSum / rmsCount).toFloat() else 0f
        return (baseline * THRESHOLD_MULTIPLIER).coerceAtLeast(MIN_THRESHOLD)
    }

    /**
     * Watches amplitude until [threshold] is crossed, records through it (with a
     * pre-buffer) until silence, transcribes, and saves if words were found.
     * Returns 1 if a clip was saved, 0 otherwise (including "no crossing" — the
     * caller loops, so this just processes one pass through monitoring).
     */
    private fun runMonitoringUntilRecordingSaved(
        audioRecord: AudioRecord,
        threshold: Float,
        model: Model,
        monitoringStart: Long,
        clipsSavedSoFar: Int,
    ): Int {
        val preBuffer = RingBuffer(PRE_BUFFER_BYTES)
        val chunk = ByteArray(CHUNK_BYTES)

        // MONITORING: wait for the threshold crossing.
        while (!stopRequested) {
            val read = audioRecord.read(chunk, 0, chunk.size)
            if (read <= 0) continue
            val rms = AmplitudeAnalyzer.rms(chunk)
            postState {
                it.copy(
                    phase = Phase.MONITORING,
                    liveAmplitude = rms,
                    elapsedSeconds = elapsedSecondsSince(monitoringStart),
                    clipsSavedThisSession = clipsSavedSoFar,
                )
            }
            if (rms > threshold) break
            preBuffer.write(chunk, 0, read)
        }
        if (stopRequested) return 0

        // RECORDING: capture through the crossing until a silence window elapses.
        val pcm = ByteArrayOutputStream()
        pcm.write(preBuffer.toByteArray())
        var silenceMillis = 0L
        val recordingStart = System.currentTimeMillis()

        while (!stopRequested) {
            val read = audioRecord.read(chunk, 0, chunk.size)
            if (read <= 0) continue
            pcm.write(chunk, 0, read)
            val rms = AmplitudeAnalyzer.rms(chunk)
            val chunkMillis = (read * 1000L) / (SAMPLE_RATE * 2)
            silenceMillis = if (rms > threshold) 0 else silenceMillis + chunkMillis

            postState {
                it.copy(
                    phase = Phase.RECORDING,
                    liveAmplitude = rms,
                    elapsedSeconds = elapsedSecondsSince(monitoringStart),
                    clipsSavedThisSession = clipsSavedSoFar,
                )
            }

            val recordedMillis = System.currentTimeMillis() - recordingStart
            if (silenceMillis >= SILENCE_STOP_MILLIS || recordedMillis >= MAX_RECORDING_MILLIS) break
        }

        return if (transcribeAndSave(pcm.toByteArray(), model)) 1 else 0
    }

    /** Runs Vosk over [pcm]; saves + inserts into the DB only if words were found. */
    private fun transcribeAndSave(pcm: ByteArray, model: Model): Boolean {
        val transcript = Recognizer(model, SAMPLE_RATE.toFloat()).use { recognizer ->
            recognizer.acceptWaveForm(pcm, pcm.size)
            JSONObject(recognizer.finalResult).optString("text", "").trim()
        }
        if (transcript.isEmpty()) return false

        val now = System.currentTimeMillis()
        val fileTimeFormat = SimpleDateFormat("HHmmss", Locale.US)
        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val file = File(recordingsDir, "${ClipRepository.fileSlug(transcript)}_${fileTimeFormat.format(now)}.wav")
        WavWriter.write(file, pcm, SAMPLE_RATE)

        repository.insert(
            label = ClipRepository.labelFor(transcript, now),
            timestamp = ClipRepository.timestampFor(now),
            transcript = transcript,
            filePath = file.absolutePath,
        )
        return true
    }

    private fun elapsedSecondsSince(startMillis: Long): Int =
        ((System.currentTimeMillis() - startMillis) / 1000L).toInt()

    /**
     * Updates [_uiState] and, if the phase or saved-clip count actually changed
     * (not just the live amplitude, which ticks many times a second), refreshes the
     * persistent notification too — that's the only "a clip was saved" feedback
     * while the app isn't in the foreground, which matters here since the person
     * using this app is asleep: no sound/vibration, just a quiet status update they
     * can glance at.
     */
    private fun postState(update: (RecordingUiState) -> RecordingUiState) {
        val previous = _uiState.value ?: RecordingUiState()
        val next = update(previous)
        _uiState.postValue(next)
        if (next.phase != previous.phase || next.clipsSavedThisSession != previous.clipsSavedThisSession) {
            notify(buildNotification(statusTextFor(next)))
        }
    }

    private fun statusTextFor(state: RecordingUiState): String {
        val phaseText = when (state.phase) {
            Phase.LOADING_MODEL -> getString(R.string.recording_status_loading)
            Phase.SETTLING -> getString(R.string.recording_status_settling)
            Phase.MONITORING -> getString(R.string.recording_status_monitoring)
            Phase.RECORDING -> getString(R.string.recording_status_recording)
        }
        return if (state.clipsSavedThisSession > 0) {
            getString(R.string.notification_clips_saved, phaseText, state.clipsSavedThisSession)
        } else {
            phaseText
        }
    }

    // ---- Notification ---------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val EXTRA_WAIT_MINUTES = "extra_wait_minutes"
        const val DEFAULT_WAIT_MINUTES = 15
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1

        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_BYTES = 3_200 // ~100ms at 16kHz mono 16-bit
        private const val PRE_BUFFER_BYTES = SAMPLE_RATE * 2 // ~1s pre-buffer
        private const val SILENCE_STOP_MILLIS = 1_500L
        private const val MAX_RECORDING_MILLIS = 30_000L
        private const val MAX_SESSION_MILLIS = 12 * 60 * 60 * 1000L // WakeLock safety cap
        private const val THREAD_JOIN_TIMEOUT_MILLIS = 3_000L
        private const val THRESHOLD_MULTIPLIER = 3.5f
        private const val MIN_THRESHOLD = 0.02f

        fun intent(context: android.content.Context, waitMinutes: Int) =
            Intent(context, RecordingService::class.java).putExtra(EXTRA_WAIT_MINUTES, waitMinutes)
    }
}
