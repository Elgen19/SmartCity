package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.google.android.libraries.places.api.model.Place

class TextSearchAdapter(
    private var places: List<Place>,
    private val onPlaceClick: (Place) -> Unit // Add this parameter
) : RecyclerView.Adapter<TextSearchAdapter.PlaceViewHolder>() {

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val placeNameTextView: TextView = itemView.findViewById(R.id.autocomplete_item_primary_text)
        val placeAddressTextView: TextView = itemView.findViewById(R.id.autocomplete_item_secondary_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.autocomplete_item, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = places[position]
        holder.placeNameTextView.text = place.name
        holder.placeAddressTextView.text = place.address // Update this line if needed to get address

        // Set the click listener for the item view
        holder.itemView.setOnClickListener {
            onPlaceClick(place) // Trigger the click callback with the selected place
        }
    }

    override fun getItemCount(): Int {
        return places.size
    }

    fun updatePlaces(newPlaces: List<Place>) {
        places = newPlaces
        notifyDataSetChanged()
    }
}
