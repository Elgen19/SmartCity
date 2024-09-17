package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
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
        // Bind event data to the view here
        holder.itemView.setOnClickListener { onItemClick(event) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(event)
            true
        }
    }

    override fun getItemCount(): Int = savedEvents.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class EventsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val eventName: TextView = itemView.findViewById(R.id.event_name)
        private val eventDescription: TextView = itemView.findViewById(R.id.event_description)

        fun bind(event: Event) {

            eventName.text = event.eventName
            eventDescription.text = event.eventDescription
        }
    }
}
