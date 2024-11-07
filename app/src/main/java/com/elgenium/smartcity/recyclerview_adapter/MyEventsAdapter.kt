package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.Event
import java.text.SimpleDateFormat
import java.util.Locale

class MyEventsAdapter(
    private var events: List<Event>, // Make this a mutable list
    private val onItemClick: (Event) -> Unit,
    private val onItemLongClick: (Event) -> Unit // Lambda for handling long-clicks
) : RecyclerView.Adapter<MyEventsAdapter.EventViewHolder>() {

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageViewEvent: ImageView = itemView.findViewById(R.id.imageViewEvent)
        val textViewEventName: TextView = itemView.findViewById(R.id.textViewEventName)
        val textViewAdditionalInfo: TextView = itemView.findViewById(R.id.textViewAdditionalInfo)
        val textViewSubmittedAt: TextView = itemView.findViewById(R.id.textViewSubmittedAt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_events, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        // Load image using Glide
        if (event.images?.isNotEmpty() == true) {
            Glide.with(holder.itemView.context)
                .load(event.images[0]) // Load the first image
                .into(holder.imageViewEvent)
        }

        holder.textViewEventName.text = event.eventName
        holder.textViewAdditionalInfo.text = event.additionalInfo

        // Format the submittedAt time
        val formattedDate = formatSubmittedAt(event.submittedAt)
        holder.textViewSubmittedAt.text = "Published on: $formattedDate"

        // Handle normal click
        holder.itemView.setOnClickListener {
            onItemClick(event) // Trigger the lambda with the clicked event
        }

        // Handle long click
        holder.itemView.setOnLongClickListener {
            onItemLongClick(event) // Trigger the lambda with the clicked event
            true // Return true to indicate the long click was handled
        }
    }

    private fun formatSubmittedAt(submittedAt: String?): String {
        return try {
            // Assuming the submittedAt string is in the format "yyyy-MM-dd HH:mm:ss"
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM d yyyy 'at' hh:mm a", Locale.getDefault())

            val date = inputFormat.parse(submittedAt!!)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            "Invalid date"
        }
    }

    override fun getItemCount(): Int = events.size

    // Method to update the events list with filtered data
    fun updateEventsList(newEvents: List<Event>) {
        this.events = newEvents
        notifyDataSetChanged() // Notify the adapter that the data has changed
    }
}
