package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.MyActivityContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyActivitiesAdapter(
    private val containerList: List<MyActivityContainer>,
    private val onItemClick: (MyActivityContainer) -> Unit,
    private val onItemLongClick: (MyActivityContainer) -> Unit
) : RecyclerView.Adapter<MyActivitiesAdapter.MyActivitiesViewHolder>() {

    inner class MyActivitiesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val containerName: TextView = itemView.findViewById(R.id.containerName)
        val dateCreated: TextView = itemView.findViewById(R.id.dateCreated)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyActivitiesViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_activity_container, parent, false)
        return MyActivitiesViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyActivitiesViewHolder, position: Int) {
        val container = containerList[position]
        holder.containerName.text = container.name

        // Update SimpleDateFormat to include both date and time
        val dateTimeFormatter = SimpleDateFormat("MMMM dd, yyyy '('hh:mm a')'", Locale.getDefault())
        val formattedDateTime = dateTimeFormatter.format(Date(container.dateCreated))  // Convert timestamp to date & time

        holder.dateCreated.text = formattedDateTime

        holder.itemView.setOnClickListener {
            onItemClick(container)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(container)
            true
        }
    }

    override fun getItemCount(): Int = containerList.size
}
