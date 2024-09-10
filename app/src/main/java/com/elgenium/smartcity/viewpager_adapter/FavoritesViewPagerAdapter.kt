package com.elgenium.smartcity.viewpager_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.SavedPlace
import com.elgenium.smartcity.recyclerview_adapter.SavedPlacesAdapter

class FavoritesViewPagerAdapter(
    private var savedPlaces: List<SavedPlace>,
    private val onItemClick: (SavedPlace) -> Unit,
    private val onItemLongClick: (SavedPlace) -> Unit
) : RecyclerView.Adapter<FavoritesViewPagerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.saved_places_view, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recyclerView: RecyclerView = holder.itemView.findViewById(R.id.recyclerViewSavedPlaces)
        recyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
        recyclerView.adapter = SavedPlacesAdapter(savedPlaces, onItemClick, onItemLongClick)
    }

    override fun getItemCount(): Int = 1 // Only one view type for now

    fun updateSavedPlaces(newSavedPlaces: List<SavedPlace>) {
        savedPlaces = newSavedPlaces
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
