package com.elgenium.smartcity.viewpager_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.Event
import com.elgenium.smartcity.models.SavedPlace
import com.elgenium.smartcity.recyclerview_adapter.SavedEventsAdapter
import com.elgenium.smartcity.recyclerview_adapter.SavedPlacesAdapter

class FavoritesViewPagerAdapter(
    private var savedPlaces: List<SavedPlace>,
    private var savedEvents: List<Event>,
    private val onPlaceClick: (SavedPlace) -> Unit,
    private val onPlaceLongClick: (SavedPlace) -> Unit,
    private val onEventClick: (Event) -> Unit,
    private val onEventLongClick: (Event) -> Unit
) : RecyclerView.Adapter<FavoritesViewPagerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view: View = when (viewType) {
            VIEW_TYPE_PLACES -> layoutInflater.inflate(R.layout.saved_places_view, parent, false)
            VIEW_TYPE_EVENTS -> layoutInflater.inflate(R.layout.saved_events_view, parent, false)
            else -> throw IllegalArgumentException("Invalid view type")
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            VIEW_TYPE_PLACES -> setupPlacesRecyclerView(holder)
            VIEW_TYPE_EVENTS -> setupEventsRecyclerView(holder)
        }
    }

    override fun getItemCount(): Int = 2 // Two pages: Places and Events

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> VIEW_TYPE_PLACES
            1 -> VIEW_TYPE_EVENTS
            else -> super.getItemViewType(position)
        }
    }

    private fun setupPlacesRecyclerView(holder: ViewHolder) {
        val recyclerView: RecyclerView = holder.itemView.findViewById(R.id.recyclerViewSavedPlaces)
        val layoutManager = LinearLayoutManager(holder.itemView.context)
        recyclerView.layoutManager = layoutManager

        // Set a custom drawable divider
        val dividerDrawable = ContextCompat.getDrawable(holder.itemView.context, R.drawable.divider_drawable)
        val dividerItemDecoration = DividerItemDecoration(holder.itemView.context, layoutManager.orientation)

        // Make sure the drawable is not null before setting it
        dividerDrawable?.let {
            dividerItemDecoration.setDrawable(it)
            recyclerView.addItemDecoration(dividerItemDecoration)
        }

        recyclerView.adapter = SavedPlacesAdapter(savedPlaces, onPlaceClick, onPlaceLongClick)
    }

    private fun setupEventsRecyclerView(holder: ViewHolder) {
        val recyclerView: RecyclerView = holder.itemView.findViewById(R.id.recyclerViewSavedEvents)
        val layoutManager = LinearLayoutManager(holder.itemView.context)
        recyclerView.layoutManager = layoutManager

        // Use the custom divider drawable
        val dividerDrawable = ContextCompat.getDrawable(recyclerView.context, R.drawable.divider_drawable)
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, layoutManager.orientation)

        // Set the divider drawable if available
        dividerDrawable?.let {
            dividerItemDecoration.setDrawable(it)
            recyclerView.addItemDecoration(dividerItemDecoration)
        }

        recyclerView.adapter = SavedEventsAdapter(savedEvents, onEventClick, onEventLongClick)
    }

    fun updateSavedPlaces(newSavedPlaces: List<SavedPlace>) {
        savedPlaces = newSavedPlaces
        notifyItemChanged(0)
    }

    fun updateSavedEvents(newSavedEvents: List<Event>) {
        savedEvents = newSavedEvents
        notifyItemChanged(1)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {
        private const val VIEW_TYPE_PLACES = 0
        private const val VIEW_TYPE_EVENTS = 1
    }
}
