package com.elgenium.smartcity.recyclerview_adapter

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.ActivityDetails
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ActivityDetailsAdapter(
    private val activityList: MutableList<ActivityDetails>,
    private val onItemClick: (ActivityDetails, Int) -> Unit // Lambda for item click
) : RecyclerView.Adapter<ActivityDetailsAdapter.ActivityViewHolder>() {

    class ActivityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val activityName: TextView = view.findViewById(R.id.tvActivityName)
        val placeName: TextView = view.findViewById(R.id.tvPlaceName)
        val placeAddress: TextView = view.findViewById(R.id.tvPlaceAddress)
        val priority: TextView = view.findViewById(R.id.tvPriority)
        val timeRange: TextView = view.findViewById(R.id.tvTimeRange)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_activity_details, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = activityList[position]

        // Set activity name, place name, and place address
        holder.activityName.text = activity.activityName
        holder.placeName.text = activity.placeName
        holder.placeAddress.text = activity.placeAddress

        holder.itemView.setOnClickListener {
            onItemClick(activity, position) // Call the lambda with the clicked activity
        }

        // Set priority text
        val priorityText = activity.priorityLevel ?: "N/A"
        holder.priority.text = priorityText
        val timeConstraintText = if (activity.startTime != null && activity.endTime != null && activity.startTime.isNotEmpty() && activity.endTime.isNotEmpty()) {
            // Format the time (assuming `startTime` and `endTime` are in "hh:mm a" format)
            val startTimeFormatted = formatTime(activity.startTime)
            val endTimeFormatted = formatTime(activity.endTime)
            "$startTimeFormatted - $endTimeFormatted"
        } else {
            "No time constraints"
        }
        holder.timeRange.text = timeConstraintText

        // Handle visibility and styling based on priority and time constraint
        if (priorityText == "N/A" || priorityText.isEmpty()) {
            holder.priority.visibility = View.GONE
        } else {
            holder.priority.visibility = View.VISIBLE
            // Set the pill badge color based on priority level
            val priorityColor = setPriorityBadgeColor(holder.priority, activity.priorityLevel)

            if (timeConstraintText == "No time constraints") {
                holder.timeRange.visibility = View.GONE
            } else {
                holder.timeRange.visibility = View.VISIBLE
                // Set the time constraint pill color based on priority color if both are set
                setTimeConstraintColor(holder.timeRange, priorityColor)
            }
        }

        // If no priority level but time constraint exists, hide the priority badge
        if (timeConstraintText == "No time constraints" && priorityText == "N/A") {
            holder.priority.visibility = View.GONE
            holder.timeRange.visibility = View.GONE
        }

        else if (timeConstraintText != "No time constraints" && priorityText != "N/A") {
            holder.priority.visibility = View.GONE
        }
    }

    // Helper function to format time to "hh:mm a" (12-hour format with AM/PM)
    private fun formatTime(time: String): String {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) // Use the correct input format
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()) // 12-hour format output with AM/PM
            val date = inputFormat.parse(time)
            return outputFormat.format(date)
        } catch (e: ParseException) {
            e.printStackTrace()
            return time // If there's an error, return the original time string as a fallback
        }
    }


    override fun getItemCount(): Int = activityList.size

    fun addActivity(activity: ActivityDetails) {
        activityList.add(activity)
        notifyItemInserted(activityList.size - 1)
    }

    // Function to set priority badge color
    private fun setPriorityBadgeColor(priorityTextView: TextView, priorityLevel: String?): Int {
        val context = priorityTextView.context
        val priorityColor: Int
        when (priorityLevel) {
            "High" -> {
                priorityColor = ContextCompat.getColor(context, R.color.red)
                priorityTextView.background.setColorFilter(priorityColor, PorterDuff.Mode.SRC_ATOP)
            }
            "Medium" -> {
                priorityColor = ContextCompat.getColor(context, R.color.bronze)
                priorityTextView.background.setColorFilter(priorityColor, PorterDuff.Mode.SRC_ATOP)
            }
            "Low" -> {
                priorityColor = ContextCompat.getColor(context, R.color.green)
                priorityTextView.background.setColorFilter(priorityColor, PorterDuff.Mode.SRC_ATOP)
            }
            else -> {
                priorityColor = ContextCompat.getColor(context, R.color.gray)
                priorityTextView.background.setColorFilter(priorityColor, PorterDuff.Mode.SRC_ATOP)
                priorityTextView.visibility = View.GONE
            }
        }
        return priorityColor
    }

    // Function to set time constraint color based on priority level
    private fun setTimeConstraintColor(timeRangeTextView: TextView, priorityColor: Int) {
        timeRangeTextView.background.setColorFilter(priorityColor, PorterDuff.Mode.SRC_ATOP)
    }

    fun removeActivity(position: Int) {
        activityList.removeAt(position)  // Remove the item from the list
        notifyItemRemoved(position)  // Notify the adapter to update the UI
    }

    fun updateActivities(newActivities: List<ActivityDetails>) {
        activityList.clear() // Clear the existing list
        activityList.addAll(newActivities) // Add new data
        notifyDataSetChanged() // Notify the adapter to refresh RecyclerView
    }




}
