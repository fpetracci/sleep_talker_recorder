package com.sleeptalker.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.sleeptalker.app.audio.AudioEnvelope
import com.sleeptalker.app.databinding.ActivityHomeBinding
import com.sleeptalker.app.model.Clip
import com.sleeptalker.app.ui.ClipAdapter

/**
 * Home screen: list of past recordings + start-a-new-session controls.
 *
 * TODO: [demoClips] stands in for a Room DB query until the recording pipeline and
 * its database exist (see README architecture). Swap it out then — the list should
 * be genuinely empty when there are no real saved recordings, so don't leave demo
 * data mixed in with real query results.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val clips = demoClips()
        binding.recyclerClips.layoutManager = LinearLayoutManager(this)
        binding.recyclerClips.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.recyclerClips.adapter = ClipAdapter(clips) { clip -> openPlayback(clip) }

        val hasClips = clips.isNotEmpty()
        binding.recyclerClips.isVisible = hasClips
        binding.textEmptyState.isVisible = !hasClips

        binding.buttonStart.setOnClickListener { startSession() }
    }

    private fun startSession() {
        val waitMinutes = binding.editWaitTimeMinutes.text?.toString()?.toIntOrNull()
            ?: RecordingActivity.DEFAULT_WAIT_MINUTES
        startActivity(RecordingActivity.newIntent(this, waitMinutes))
    }

    private fun openPlayback(clip: Clip) {
        startActivity(PlaybackActivity.newIntent(this, clip))
    }

    /**
     * Demo data for testing the GUI, backed by the real audio in tests/samples/
     * (bundled as app assets — see app/src/main/assets/samples/). Not a stand-in for
     * "no recordings yet": once the real pipeline exists, an empty result set here
     * should leave the list genuinely empty, not fall back to these.
     */
    private fun demoClips(): List<Clip> = listOf(
        Clip(
            id = 1,
            label = "first words_00:00 (demo)",
            timestamp = "00:00",
            transcript = "wow morti scuse non erano via",
            filePath = "${AudioEnvelope.ASSET_SCHEME}samples/test_sample.mp4",
        ),
        Clip(
            id = 2,
            label = "sample_humming (demo — no words, would be discarded)",
            timestamp = "00:00",
            transcript = "",
            filePath = "${AudioEnvelope.ASSET_SCHEME}samples/sample_humming.m4a",
        ),
        Clip(
            id = 3,
            label = "sample_coughin (demo — false-positive \"il\")",
            timestamp = "00:00",
            transcript = "il",
            filePath = "${AudioEnvelope.ASSET_SCHEME}samples/sample_coughin.m4a",
        ),
    )
}
