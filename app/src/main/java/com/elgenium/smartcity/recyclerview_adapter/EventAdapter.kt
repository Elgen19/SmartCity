package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.ItemEventDetailBinding
import com.elgenium.smartcity.models.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class EventAdapter(
    private var events: List<Event>,
    private val onItemClick: (Event) -> Unit,
    private val lottieAnimation: LottieAnimationView,
    private val emptyDataLabel: TextView
) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    private var allEvents: List<Event> = events

    init {
        updateUI()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemEventDetailBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.bind(event)
    }

    override fun getItemCount(): Int = events.size

    fun filter(query: String) {
        events = if (query.isEmpty()) {
            allEvents
        } else {
            allEvents.filter {
                it.eventName?.contains(query, ignoreCase = true) == true ||
                         it.location?.contains(query, ignoreCase = true) == true
            }
        }
        notifyDataSetChanged() // Notify adapter about data changes
        updateUI()
    }


    fun updateEvents(newEvents: List<Event>) {
        events = newEvents
        notifyDataSetChanged()
        updateUI()
    }

    private fun updateUI() {
        if (events.isEmpty()) {
            lottieAnimation.visibility = View.VISIBLE
            emptyDataLabel.visibility = View.VISIBLE
        } else {
            lottieAnimation.visibility = View.GONE
            emptyDataLabel.visibility = View.GONE
        }
    }

    inner class EventViewHolder(private val binding: ItemEventDetailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Set up click listener on the whole item view
            itemView.setOnClickListener {
                val event = events[adapterPosition]
                onItemClick(event) // Trigger the click event
            }
        }

        fun bind(event: Event) {
            binding.eventName.text = event.eventName
            binding.placeName.text = event.location
            binding.placeAddress.text = event.additionalInfo
            binding.submittedDate.text = formatDate(event.submittedAt ?: "")
            binding.submittedBy.text = "By: ${event.submittedBy}"
            binding.eventCategory.text = event.eventCategory

            // Load the event image
            val imageUrl = event.images?.getOrNull(0) // Get the first image URL if available
            Glide.with(binding.eventImage.context)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_viewpager_photos)
                .into(binding.eventImage)

            // Set up visibility for status icons based on the event's status
            val statuses = event.status?.split(",")?.map { it.trim() } ?: emptyList()

            // Determine visibility for Verified status
            if ("Verified" in statuses) {
                binding.eventStatus.visibility = View.VISIBLE
            } else {
                binding.eventStatus.visibility = View.GONE // Hide if not verified
            }

            // Determine visibility for Peer Reviewed status
            if ("Peer_Reviewed" in statuses) {
                binding.eventPeerReviewed.visibility = View.VISIBLE
            } else {
                binding.eventPeerReviewed.visibility = View.GONE // Hide if not peer reviewed
            }
        }

        private fun formatDate(submittedAt: String): String {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

            return try {
                val date: Date = inputFormat.parse(submittedAt) ?: Date()
                outputFormat.format(date)
            } catch (e: Exception) {
                submittedAt // Return original string if there's an error
            }
        }
    }
}
