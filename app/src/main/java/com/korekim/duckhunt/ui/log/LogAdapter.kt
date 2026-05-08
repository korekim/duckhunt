package com.korekim.duckhunt.ui.log

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.korekim.duckhunt.ui.log.LogEntry
import com.korekim.duckhunt.R

class LogAdapter(private val entries: List<LogEntry>) :
    RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvLogLabel)
        val tvTimestamp: TextView = view.findViewById(R.id.tvLogTimestamp)
        val tvCoords: TextView = view.findViewById(R.id.tvLogCoords)
        val tvTags: TextView = view.findViewById(R.id.tvLogTags)
        val btnMap: ImageButton = view.findViewById(R.id.btnLogMap)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.tvLabel.text = entry.label
        holder.tvTimestamp.text = entry.timestamp
        holder.tvCoords.text = "${"%.5f".format(entry.lat)}, ${"%.5f".format(entry.lon)}"

        // format tags back from ~ delimited to readable
        val tagsFormatted = entry.tags.split("~")
            .filter { it.isNotBlank() }
            .joinToString("\n")
        holder.tvTags.text = tagsFormatted

        // toggle tags visibility on click
        holder.itemView.setOnClickListener {
            if (holder.tvTags.visibility == View.GONE) {
                holder.tvTags.visibility = View.VISIBLE
            } else {
                holder.tvTags.visibility = View.GONE
            }
        }

        holder.btnMap.setOnClickListener {
            val uri = Uri.parse("https://sunders.uber.space/?zoom=17&lat=${entry.lat}&lon=${entry.lon}")
            it.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    override fun getItemCount() = entries.size
}