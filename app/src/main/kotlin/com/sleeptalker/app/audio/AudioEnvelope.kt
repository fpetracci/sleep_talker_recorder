package com.sleeptalker.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes an audio file into a fixed-length amplitude envelope for
 * [com.sleeptalker.app.ui.WaveformView] — the real per-clip waveform, rather than a
 * random placeholder squiggle.
 *
 * Decoding (MediaExtractor + MediaCodec) runs off the main thread; results are
 * cached by source key since the same clip can be bound to a RecyclerView row many
 * times as the list scrolls.
 */
object AudioEnvelope {

    const val ASSET_SCHEME = "asset://"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, List<Float>>()

    /** [source] is either "asset://<name under assets/>" or an absolute filesystem path. */
    fun decodeAsync(context: Context, source: String, points: Int, onResult: (List<Float>) -> Unit) {
        cache[source]?.let { onResult(it); return }
        val appContext = context.applicationContext
        executor.execute {
            val envelope = try {
                decode(appContext, source, points)
            } catch (e: Exception) {
                emptyList()
            }
            if (envelope.isNotEmpty()) cache[source] = envelope
            mainHandler.post { onResult(envelope) }
        }
    }

    private fun decode(context: Context, source: String, points: Int): List<Float> {
        val extractor = MediaExtractor()
        if (source.startsWith(ASSET_SCHEME)) {
            val name = source.removePrefix(ASSET_SCHEME)
            context.assets.openFd(name).use { afd ->
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
        } else {
            extractor.setDataSource(source)
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { extractor.release(); return emptyList() }

        val format = extractor.getTrackFormat(trackIndex)
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcm = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)!!
                    outBuffer.position(bufferInfo.offset)
                    outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val bytes = ByteArray(bufferInfo.size)
                    outBuffer.get(bytes)
                    pcm.write(bytes)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        return binToEnvelope(pcm.toByteArray(), points)
    }

    private fun binToEnvelope(pcmBytes: ByteArray, points: Int): List<Float> {
        val shortCount = pcmBytes.size / 2
        if (shortCount == 0) return emptyList()

        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val samplesPerBin = max(1, shortCount / points)
        val envelope = FloatArray(points)

        for (bin in 0 until points) {
            val start = bin * samplesPerBin
            val end = if (bin == points - 1) shortCount else min(shortCount, start + samplesPerBin)
            if (start >= end) continue
            var sum = 0L
            for (i in start until end) sum += abs(buffer.getShort(i * 2).toInt())
            envelope[bin] = (sum / (end - start)).toFloat()
        }

        val maxVal = envelope.maxOrNull()?.takeIf { it > 0f } ?: return emptyList()
        return envelope.map { (it / maxVal).coerceIn(0f, 1f) }
    }
}
