package com.sleeptalker.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sleeptalker.app.audio.AudioEnvelope
import com.sleeptalker.app.databinding.ItemClipBinding
import com.sleeptalker.app.model.Clip

class ClipAdapter(
    private val clips: List<Clip>,
    private val onClipClicked: (Clip) -> Unit,
) : RecyclerView.Adapter<ClipAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemClipBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val clip = clips[position]
        holder.binding.textLabel.text = clip.label
        holder.binding.waveformIcon.samples = emptyList()
        holder.itemView.setOnClickListener { onClipClicked(clip) }

        val filePath = clip.filePath ?: return
        holder.itemView.tag = clip.id
        AudioEnvelope.decodeAsync(holder.itemView.context, filePath, WAVEFORM_ICON_POINTS) { envelope ->
            // The holder may have been rebound to a different clip by the time
            // decoding finishes (RecyclerView reuse during fast scrolling).
            if (holder.itemView.tag == clip.id) {
                holder.binding.waveformIcon.samples = envelope
            }
        }
    }

    override fun getItemCount(): Int = clips.size

    private companion object {
        const val WAVEFORM_ICON_POINTS = 24
    }
}
