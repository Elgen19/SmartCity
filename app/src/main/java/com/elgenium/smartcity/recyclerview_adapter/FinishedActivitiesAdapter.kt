package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.ActivityDetails

class FinishedActivitiesAdapter(
    private val activityList: List<ActivityDetails>,
    private val onItemClick: (ActivityDetails) -> Unit  // Lambda for item click
) : RecyclerView.Adapter<FinishedActivitiesAdapter.FinishedActivityViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FinishedActivityViewHolder {
        val binding = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_details, parent, false)
        return FinishedActivityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FinishedActivityViewHolder, position: Int) {
        val activity = activityList[position]
        holder.bind(activity)
    }

    override fun getItemCount(): Int = activityList.size

    inner class FinishedActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val stateIcons: ImageView = itemView.findViewById(R.id.stateIcons)
        private val tvActivityName: TextView = itemView.findViewById(R.id.tvActivityName)
        private val tvPriority: TextView = itemView.findViewById(R.id.tvPriority)
        private val tvTimeRange: TextView = itemView.findViewById(R.id.tvTimeRange)
        private val tvPlaceName: TextView = itemView.findViewById(R.id.tvPlaceName)
        private val tvPlaceAddress: TextView = itemView.findViewById(R.id.tvPlaceAddress)

        fun bind(activity: ActivityDetails) {
            // Set the data for each item
            tvActivityName.text = activity.activityName
            tvPriority.text = activity.priorityLevel
            tvTimeRange.text = activity.startTime
            tvPlaceName.text = activity.placeName
            tvPlaceAddress.text = activity.placeAddress
            stateIcons.visibility = View.GONE

            // Item click listener
            itemView.setOnClickListener {
                onItemClick(activity)  // Invoke lambda function when item is clicked
            }
        }
    }
}
