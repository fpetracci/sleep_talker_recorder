package com.sleeptalker.app

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.sleeptalker.app.audio.AudioEnvelope
import com.sleeptalker.app.databinding.ActivityPlaybackBinding
import java.io.File

/**
 * Playback / detail screen for a single saved clip.
 *
 * TODO: real saved clips (once the recording pipeline exists) will have a
 * filesystem [Clip.filePath] rather than the "asset://" scheme the bundled
 * tests/samples demo clips use — both are already handled here.
 */
class PlaybackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaybackBinding
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Polls MediaPlayer's position while playing, to drive the waveform's playhead. */
    private val progressTick = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.duration > 0) {
                    binding.waveformClip.progress = player.currentPosition.toFloat() / player.duration
                }
            }
            handler.postDelayed(this, PROGRESS_TICK_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val transcript = intent.getStringExtra(EXTRA_TRANSCRIPT).orEmpty()
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)

        binding.textClipLabel.text = label
        binding.textTranscript.text = transcript

        if (filePath != null) {
            AudioEnvelope.decodeAsync(this, filePath, WAVEFORM_POINTS) { envelope ->
                binding.waveformClip.samples = envelope
            }
        }

        setUpPlayback(filePath)
        setUpVolumeBoost()
    }

    private fun setUpPlayback(filePath: String?) {
        if (filePath == null) {
            binding.buttonPlayPause.isEnabled = false
            binding.buttonPlayPause.text = getString(R.string.playback_unavailable)
            return
        }

        binding.waveformClip.progress = 0f
        binding.waveformClip.onSeek = { fraction ->
            binding.waveformClip.progress = fraction
            mediaPlayer?.let { player ->
                if (player.duration > 0) player.seekTo((fraction * player.duration).toInt())
            }
        }

        binding.buttonPlayPause.setOnClickListener {
            val player = mediaPlayer
            if (player == null) {
                startPlayback(filePath)
            } else if (player.isPlaying) {
                player.pause()
                binding.buttonPlayPause.text = getString(R.string.playback_play)
            } else {
                player.start()
                binding.buttonPlayPause.text = getString(R.string.playback_pause)
            }
        }
    }

    private fun startPlayback(filePath: String) {
        mediaPlayer = MediaPlayer().apply {
            if (filePath.startsWith(AudioEnvelope.ASSET_SCHEME)) {
                val name = filePath.removePrefix(AudioEnvelope.ASSET_SCHEME)
                assets.openFd(name).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            } else {
                setDataSource(File(filePath).absolutePath)
            }
            setOnCompletionListener {
                binding.buttonPlayPause.text = getString(R.string.playback_play)
                binding.waveformClip.progress = 0f
                it.seekTo(0)
            }
            prepare()
        }
        loudnessEnhancer = LoudnessEnhancer(mediaPlayer!!.audioSessionId)
        applyVolumeBoost(binding.sliderVolumeBoost.progress)
        mediaPlayer?.start()
        handler.post(progressTick)
        binding.buttonPlayPause.text = getString(R.string.playback_pause)
    }

    /**
     * Vertical slider (0-100 = plain attenuation, 100-200 = amplification above
     * unity gain). MediaPlayer's own volume tops out at 1.0 (100%), so boosting past
     * that needs [LoudnessEnhancer] instead — up to +20dB at the slider's max.
     */
    private fun setUpVolumeBoost() {
        binding.sliderVolumeBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                applyVolumeBoost(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun applyVolumeBoost(sliderValue: Int) {
        val fraction = sliderValue / 100f // 0.0..2.0
        val volume = fraction.coerceAtMost(1f)
        mediaPlayer?.setVolume(volume, volume)

        val boostAboveUnity = (fraction - 1f).coerceAtLeast(0f) // 0.0..1.0
        val gainMillibels = (boostAboveUnity * MAX_BOOST_DB * 100).toInt()
        loudnessEnhancer?.apply {
            setTargetGain(gainMillibels)
            enabled = gainMillibels > 0
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressTick)
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        private const val EXTRA_CLIP_ID = "extra_clip_id"
        private const val EXTRA_LABEL = "extra_label"
        private const val EXTRA_TRANSCRIPT = "extra_transcript"
        private const val EXTRA_FILE_PATH = "extra_file_path"
        private const val WAVEFORM_POINTS = 64
        private const val PROGRESS_TICK_MILLIS = 150L
        private const val MAX_BOOST_DB = 20f

        fun newIntent(context: Context, clip: com.sleeptalker.app.model.Clip): Intent =
            Intent(context, PlaybackActivity::class.java)
                .putExtra(EXTRA_CLIP_ID, clip.id)
                .putExtra(EXTRA_LABEL, clip.label)
                .putExtra(EXTRA_TRANSCRIPT, clip.transcript)
                .putExtra(EXTRA_FILE_PATH, clip.filePath)
    }
}
