package com.sleeptalker.app.audio

import android.content.Context
import com.sleeptalker.app.BuildConfig
import org.vosk.Model
import java.io.File

/**
 * Loads the Vosk STT [Model] bundled under assets/[BuildConfig.VOSK_MODEL_NAME]/.
 *
 * Vosk's native code needs a real filesystem directory (it can't read straight out
 * of the compressed APK), so the model is copied to app-private storage once and
 * reused on every later launch — a fresh copy is only made if [MARKER_FILE] is
 * missing (guards against a partial copy from a previous crash/kill).
 */
object VoskModelProvider {

    private const val MARKER_FILE = ".unpacked"

    /** Blocking — copies ~90MB on first run. Always call from a background thread. */
    fun load(context: Context): Model {
        val modelDir = File(context.filesDir, BuildConfig.VOSK_MODEL_NAME)
        val marker = File(modelDir, MARKER_FILE)
        if (!marker.exists()) {
            modelDir.deleteRecursively()
            modelDir.mkdirs()
            copyAssetDir(context, BuildConfig.VOSK_MODEL_NAME, modelDir)
            marker.createNewFile()
        }
        return Model(modelDir.absolutePath)
    }

    private fun copyAssetDir(context: Context, assetPath: String, destDir: File) {
        val entries = context.assets.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            // Leaf file, not a directory.
            destDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destDir.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        destDir.mkdirs()
        for (entry in entries) {
            copyAssetDir(context, "$assetPath/$entry", File(destDir, entry))
        }
    }
}
