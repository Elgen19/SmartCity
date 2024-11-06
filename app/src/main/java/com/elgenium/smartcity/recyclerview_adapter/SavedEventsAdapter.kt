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

class SavedEventsAdapter(
    private var savedEvents: List<Event>,
    private val onItemClick: (Event) -> Unit,
    private val onItemLongClick: (Event) -> Unit
) : RecyclerView.Adapter<SavedEventsAdapter.EventsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventsViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.item_saved_event, parent, false)
        return EventsViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventsViewHolder, position: Int) {
        val event = savedEvents[position]
        holder.bind(event)

        // Set click listeners
        holder.itemView.setOnClickListener { onItemClick(event) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(event)
            true
        }
    }

    override fun getItemCount(): Int = savedEvents.size

    inner class EventsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val eventName: TextView = itemView.findViewById(R.id.event_name)
        private val eventDescription: TextView = itemView.findViewById(R.id.event_description)
        private val placeImage: ImageView = itemView.findViewById(R.id.event_image)

        fun bind(event: Event) {
            eventName.text = event.eventName
            eventDescription.text = event.eventDescription

            // Load the first image from the images list, if available
            val imageUrl = event.images?.firstOrNull()
            if (imageUrl != null) {
                // Use Glide to load the image from the URL
                Glide.with(itemView.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder_viewpager_photos) // Fallback placeholder image
                    .into(placeImage)
            } else {
                // Load placeholder if no image URL is available
                placeImage.setImageResource(R.drawable.placeholder_viewpager_photos)
            }
        }
    }
}
