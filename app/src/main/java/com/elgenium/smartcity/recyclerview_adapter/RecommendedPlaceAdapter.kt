package com.elgenium.smartcity.recyclerview_adapter

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.RecommendedPlace
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient

class RecommendedPlaceAdapter(
    private val places: List<RecommendedPlace>,
    private val showRating: Boolean,
    private val placesClient: PlacesClient,  // Add the PlacesClient parameter here
    private val onPlaceClick: (RecommendedPlace) -> Unit  // Lambda for click handling
) : RecyclerView.Adapter<RecommendedPlaceAdapter.PlaceViewHolder>() {

    class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val placeName: TextView = view.findViewById(R.id.placeName)
        val placeAddress: TextView = view.findViewById(R.id.placeAddress)
        val placeImage: ImageView = view.findViewById(R.id.placeImage) // Add ImageView here
        val placeDistance: TextView = view.findViewById(R.id.placeDistance)
        val placeRating: TextView = view.findViewById(R.id.placeRating)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recomemended_places, parent, false)
        return PlaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        val place = places[position]
        holder.placeName.text = place.name
        holder.placeAddress.text = place.address

        Log.e("PlacesActivity", "DISTANCE: ${place.distanceString}")

        // Extract the numeric part from the distance string (e.g., "2.9 km" becomes "2.9")
        val distanceNumericString = place.distanceString.replace("[^\\d.]".toRegex(), "")

        // Ensure the extracted distance is valid
        if (distanceNumericString.isNotEmpty()) {
            val distanceInKm = distanceNumericString.toDoubleOrNull() ?: 0.0

            // Convert to meters for comparison (1 km = 1000 meters)
            val distanceInMeters = distanceInKm * 1000

            // Format distance for display
            val formattedDistance = String.format("%.2f km away", distanceInKm) // Keep two decimal places
            holder.placeDistance.text = formattedDistance

            // Set text color based on the numeric distance in meters
            holder.placeDistance.setTextColor(when {
                distanceInMeters <= 1000 -> Color.GREEN // Walkable distance (≤ 1000 meters)
                distanceInMeters <= 2000 -> ContextCompat.getColor(holder.itemView.context, R.color.bronze) // Moderate distance (1001 - 2000 meters)
                else -> Color.RED // Far distance (> 1500 meters)
            })
        } else {
            // Handle case where distance is not available or invalid
            holder.placeDistance.text = holder.itemView.context.getString(R.string.distance_not_available)
            holder.placeDistance.setTextColor(Color.GRAY) // Default color for unavailable distance
        }

        // Load the photo if available
        place.photoMetadata?.let { photoMetadata ->
            fetchPhoto(photoMetadata, holder.placeImage) // Pass ImageView directly
        } ?: run {
            // Set a placeholder image if no photo is available
            Glide.with(holder.placeImage.context)
                .load(R.drawable.placeholder_viewpager_photos)
                .into(holder.placeImage)
        }


        if (showRating) {
            holder.placeRating.visibility = View.VISIBLE
            holder.placeRating.text = String.format("%.1f star ratings", place.rating)

            // Check if rating is greater than 3.1
            val backgroundResource = if (place.rating > 3.1) {
                R.drawable.best_pill_bg  // Use the best pill background
            } else {
                R.drawable.not_best_pill_bg  // Use the not-best pill background
            }

            // Set the background resource for the rating TextView
            holder.placeRating.setBackgroundResource(backgroundResource)
        } else {
            // Handle case where rating is null or unavailable
            holder.placeRating.visibility = View.GONE
            holder.placeRating.setBackgroundResource(R.drawable.not_best_pill_bg) // Default to not-best background
        }

        // Set click listener for the item view
        holder.itemView.setOnClickListener {
            onPlaceClick(place)  // Trigger the click callback with the place data
        }
    }


    override fun getItemCount() = places.size

    // Fetch photo method to be used within the adapter
    private fun fetchPhoto(photoMetadata: PhotoMetadata, imageView: ImageView) {
        val photoRequest = FetchPhotoRequest.builder(photoMetadata)
            .setMaxWidth(800)
            .setMaxHeight(800)
            .build()

        placesClient.fetchPhoto(photoRequest)
            .addOnSuccessListener { response ->
                val bitmap = response.bitmap
                // Use Glide to load the bitmap into the ImageView
                Glide.with(imageView.context)
                    .load(bitmap)
                    .placeholder(R.drawable.placeholder_viewpager_photos)  // Placeholder image
                    .error(R.drawable.error_image)  // Error image
                    .into(imageView) // Set the bitmap into the ImageView
            }
            .addOnFailureListener { exception ->
                Log.e("PlacesActivity", "Error fetching photo", exception)
                // Optionally, set a placeholder or error image using Glide
                Glide.with(imageView.context)
                    .load(R.drawable.error_image)
                    .into(imageView)
            }
    }

}
