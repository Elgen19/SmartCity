package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.network_reponses.Routes

class RoutePickerAdapter(
    private val routes: List<Routes>, // List of routes
    private val onRouteClick: (Routes, Int) -> Unit // Click listener for each route
) : RecyclerView.Adapter<RoutePickerAdapter.RouteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_route_picker, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position] // Access the route object at the current position
        holder.bind(route, position)
    }

    override fun getItemCount(): Int = routes.size

    inner class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionTextView: TextView = itemView.findViewById(R.id.routeDescriptionTextView)
        private val distanceTextView: TextView = itemView.findViewById(R.id.distanceValueTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.travelTimeValueTextView)

        fun bind(route: Routes, position: Int) {
            // Handle description: Default to "Alternate Route {position + 1}" if null
            val description = route.description ?: "Alternate Route ${position + 1}"
            descriptionTextView.text = description

            // Handle distance conversion to kilometers
            val distanceKm = route.distanceMeters / 1000.0
            distanceTextView.text = String.format("%.2f km", distanceKm)

            // Handle duration conversion from seconds
            val durationSeconds = route.duration.replace("s", "").toInt() // Remove 's' and convert to seconds
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60

            // Build formatted duration string, excluding zero values
            val formattedDuration = buildString {
                if (hours > 0) append("$hours h ")
                if (minutes > 0) append("$minutes min ")
                if (seconds > 0 || (hours == 0 && minutes == 0)) append("$seconds s")
            }.trim()

            durationTextView.text = formattedDuration

            // Set click listener for each item
            itemView.setOnClickListener {
                onRouteClick(route, position) // Pass the route and position to the listener
            }
        }
    }
}
