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
            allEvents.filter { it.eventName?.contains(query, ignoreCase = true) == true }
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

            val imageUrl = event.images?.getOrNull(0) // Get the first image URL if available

            Glide.with(binding.eventImage.context)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_viewpager_photos)
                .into(binding.eventImage)

        }


    }
}
