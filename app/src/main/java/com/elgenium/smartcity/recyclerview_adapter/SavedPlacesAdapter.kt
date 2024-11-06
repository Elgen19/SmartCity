package com.elgenium.smartcity.recyclerview_adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.SavedPlace

class SavedPlacesAdapter(
    private val savedPlaces: List<SavedPlace>,
    private val onItemClick: (SavedPlace) -> Unit,
    private val onItemLongClick: (SavedPlace) -> Unit
) : RecyclerView.Adapter<SavedPlacesAdapter.PlaceViewHolder>() {

    private val TAG = "SavedPlacesAdapter"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        Log.d(TAG, "onCreateViewHolder: Creating new view holder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_place, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder: Binding view holder at position $position")
        val place = savedPlaces[position]
        holder.bind(place)
        holder.itemView.setOnClickListener {
            Log.d(TAG, "Item clicked: ${place.name}")
            onItemClick(place)
        }

        holder.itemView.setOnLongClickListener {
            Log.d(TAG, "Item long-clicked: ${place.name}")
            onItemLongClick(place)
            true  // Return true to indicate the event has been consumed
        }
    }

    override fun getItemCount(): Int {
        val count = savedPlaces.size
        Log.d(TAG, "getItemCount: Total item count $count")
        return count
    }

    inner class PlaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val placeName: TextView = itemView.findViewById(R.id.placeName)
        private val placeAddress: TextView = itemView.findViewById(R.id.placeAddress)
        private val placeImage: ImageView = itemView.findViewById(R.id.placeImage)

        fun bind(place: SavedPlace) {
            Log.d(TAG, "bind: Binding place ${place.name}")
            placeName.text = place.name
            placeAddress.text = place.address

            // Load the first image from the imageUrls list, if available
            val imageUrl = place.imageUrls.firstOrNull()
            if (imageUrl != null) {
                Glide.with(itemView.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder_viewpager_photos) // Fallback placeholder
                    .into(placeImage)
            } else {
                // Set placeholder if no image is available
                placeImage.setImageResource(R.drawable.placeholder_viewpager_photos)
            }
        }
    }
}
