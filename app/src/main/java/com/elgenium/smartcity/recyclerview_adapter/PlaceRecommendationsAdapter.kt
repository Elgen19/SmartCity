package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.databinding.ItemLocationBasedRecommendationsBinding

class PlaceRecommendationsAdapter(
    private val placesInfo: List<Map<String, Any>>,
    private val onPlaceClicked: (Map<String, Any>) -> Unit
) : RecyclerView.Adapter<PlaceRecommendationsAdapter.PlaceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val binding = ItemLocationBasedRecommendationsBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PlaceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val placeInfo = placesInfo[position]
        holder.bind(placeInfo)
        holder.itemView.setOnClickListener { onPlaceClicked(placeInfo) }
    }

    override fun getItemCount(): Int = placesInfo.size

    inner class PlaceViewHolder(private val binding: ItemLocationBasedRecommendationsBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(placeInfo: Map<String, Any>) {
            // Extracting place data from the map
            val name = placeInfo["name"] as? String ?: "Unknown"
            val address = placeInfo["address"] as? String ?: "No Address"
            val rating = placeInfo["rating"] as? Double ?: 0.0
            val distance = placeInfo["distance"] as? String ?: "N/A"

            // Bind the data to the views
            binding.tvPlaceName.text = name
            binding.tvPlaceAddress.text = address
            binding.tvRatings.text = rating.toString()
            binding.tvDistance.text = distance
        }
    }
}
