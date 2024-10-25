package com.elgenium.smartcity.recyclerview_adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.network_reponses.RouteTravelMode
import com.elgenium.smartcity.network_reponses.Routes

class RoutePickerAdapter(
    private val routes: List<Routes>,
    private val onRouteClick: (Int) -> Unit
) : RecyclerView.Adapter<RoutePickerAdapter.RouteViewHolder>() {

    private var selectedPosition = RecyclerView.NO_POSITION

    // Map unique routes to their original indices
    private val uniqueRoutesWithOriginalIndex = generateUniqueRoutesWithIndices(routes)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_route_picker, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val (route, description, originalIndex) = uniqueRoutesWithOriginalIndex[position]

        holder.bind(route, description, position == selectedPosition)

        // Handle item clicks
        holder.itemView.setOnClickListener {
            val previousSelectedPosition = selectedPosition
            selectedPosition = position

            notifyItemChanged(previousSelectedPosition)  // Deselect previous
            notifyItemChanged(selectedPosition)          // Select current

            // Trigger route click action with the correct original route index
            onRouteClick(originalIndex)
        }
    }

    override fun getItemCount(): Int = uniqueRoutesWithOriginalIndex.size

    inner class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionTextView: TextView = itemView.findViewById(R.id.routeDescriptionTextView)
        private val distanceTextView: TextView = itemView.findViewById(R.id.distanceValueTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.travelTimeValueTextView)

        fun bind(route: Routes, description: String, isSelected: Boolean) {
            descriptionTextView.text = description

            val distanceKm = route.distanceMeters / 1000.0
            distanceTextView.text = String.format("%.2f km", distanceKm)

            val durationSeconds = route.duration.replace("s", "").toInt()
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60

            durationTextView.text = buildString {
                if (hours > 0) append("$hours h ")
                if (minutes > 0) append("$minutes min ")
                if (seconds > 0 || (hours == 0 && minutes == 0)) append("$seconds s")
            }.trim()

            // Highlight item if selected
            itemView.setBackgroundColor(
                if (isSelected) Color.LTGRAY else Color.TRANSPARENT
            )
        }
    }

    private fun generateUniqueRoutesWithIndices(routes: List<Routes>): List<Triple<Routes, String, Int>> {
        val uniqueRoutes = mutableListOf<Triple<Routes, String, Int>>()

        routes.forEachIndexed { index, route ->
            val description = route.legs.joinToString(", ") { leg ->
                leg.steps.joinToString(", ") { step ->
                    when (step.travelMode) {
                        RouteTravelMode.WALK -> "Walk"
                        RouteTravelMode.TRANSIT -> step.transitDetails.transitLine?.nameShort?.let { "Ride $it" } ?: ""
                        else -> ""
                    }
                }
            }.split(", ").distinct().joinToString(", ")

            // Add only if the description is unique
            if (uniqueRoutes.none { it.second == description }) {
                uniqueRoutes.add(Triple(route, description.ifEmpty { "Via ${route.description}" }, index))
            }
        }
        return uniqueRoutes
    }
}
