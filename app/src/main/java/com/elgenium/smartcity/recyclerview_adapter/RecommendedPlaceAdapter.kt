
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.RecommendedPlace
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient

class RecommendedPlaceAdapter(
    private val places: List<RecommendedPlace>,
    private val placesClient: PlacesClient,  // Add the PlacesClient parameter here
    private val onPlaceClick: (RecommendedPlace) -> Unit  // Lambda for click handling
) : RecyclerView.Adapter<RecommendedPlaceAdapter.PlaceViewHolder>() {

    class PlaceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val placeName: TextView = view.findViewById(R.id.placeName)
        val placeAddress: TextView = view.findViewById(R.id.placeAddress)
        val placeImage: ImageView = view.findViewById(R.id.placeImage) // Add ImageView here
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

        // Load the photo if available
        place.photoMetadata?.let { photoMetadata ->
            fetchPhoto(photoMetadata, holder.placeImage) // Pass ImageView directly
        } ?: run {
            // Set a placeholder image if no photo is available
            Glide.with(holder.placeImage.context)
                .load(R.drawable.placeholder_viewpager_photos)
                .into(holder.placeImage)
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
