package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.LocationBasedPlaceRecommendationItems

class LocationBasedPlaceRecommendationAdapter(
    private val placeList: List<LocationBasedPlaceRecommendationItems>,
    private val onItemClick: (LocationBasedPlaceRecommendationItems) -> Unit // Lambda for click listener
) : RecyclerView.Adapter<LocationBasedPlaceRecommendationAdapter.PlaceViewHolder>() {

    class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val placeName: TextView = view.findViewById(R.id.tvPlaceName)
        val placeAddress: TextView = view.findViewById(R.id.tvPlaceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_based_recommendations, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = placeList[position]
        holder.placeName.text = place.name
        holder.placeAddress.text = place.address

        // Set an onClick listener to invoke the lambda
        holder.itemView.setOnClickListener {
            onItemClick(place) // Pass the clicked item to the lambda
        }
    }

    override fun getItemCount(): Int {
        return placeList.size
    }
}
