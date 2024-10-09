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

class RecommendedEventAdapter(private val events: List<Event>) : RecyclerView.Adapter<RecommendedEventAdapter.EventViewHolder>() {

    // ViewHolder class
    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val eventImageView: ImageView = itemView.findViewById(R.id.eventImageView)
        private val eventNameTextView: TextView = itemView.findViewById(R.id.eventNameTextView)
        private val eventAddressTextView: TextView = itemView.findViewById(R.id.eventAddressTextView)
        private val eventCategoryTextView: TextView = itemView.findViewById(R.id.eventCategoryTextView)

        fun bind(event: Event) {
            eventNameTextView.text = event.eventName
            eventAddressTextView.text = event.additionalInfo // Assuming additionalInfo contains the address
            eventCategoryTextView.text = event.eventCategory
            // Load image using an image loading library (e.g., Glide, Picasso)
            Glide.with(itemView.context)
                .load(event.images?.firstOrNull()) // Load the first image or a placeholder
                .placeholder(R.drawable.placeholder_viewpager_photos) // Placeholder image
                .into(eventImageView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recommended_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount(): Int = events.size
}
