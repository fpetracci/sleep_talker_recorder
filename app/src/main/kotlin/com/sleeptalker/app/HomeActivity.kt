package com.sleeptalker.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.sleeptalker.app.data.ClipRepository
import com.sleeptalker.app.databinding.ActivityHomeBinding
import com.sleeptalker.app.model.Clip
import com.sleeptalker.app.recording.RecordingService
import com.sleeptalker.app.ui.ClipAdapter

/** Home screen: list of past recordings (see [ClipRepository]) + start-a-new-session controls. */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var repository: ClipRepository
    private lateinit var adapter: ClipAdapter

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            launchRecordingSession()
        } else {
            Toast.makeText(this, R.string.recording_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = ClipRepository(this)

        adapter = ClipAdapter(emptyList()) { clip -> openPlayback(clip) }
        binding.recyclerClips.layoutManager = LinearLayoutManager(this)
        binding.recyclerClips.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        binding.recyclerClips.adapter = adapter

        repository.clips.observe(this) { clips -> renderClips(clips) }

        binding.buttonStart.setOnClickListener { startSession() }
    }

    private fun renderClips(clips: List<Clip>) {
        adapter = ClipAdapter(clips) { clip -> openPlayback(clip) }
        binding.recyclerClips.adapter = adapter

        val hasClips = clips.isNotEmpty()
        binding.recyclerClips.isVisible = hasClips
        binding.textEmptyState.isVisible = !hasClips
    }

    private fun startSession() {
        val requiredPermissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            launchRecordingSession()
        } else {
            requestPermissions.launch(missing.toTypedArray())
        }
    }

    private fun launchRecordingSession() {
        val waitMinutes = binding.editWaitTimeMinutes.text?.toString()?.toIntOrNull()
            ?: RecordingService.DEFAULT_WAIT_MINUTES
        ContextCompat.startForegroundService(this, RecordingService.intent(this, waitMinutes))
        startActivity(Intent(this, RecordingActivity::class.java))
    }

    private fun openPlayback(clip: Clip) {
        startActivity(PlaybackActivity.newIntent(this, clip))
    }
}
