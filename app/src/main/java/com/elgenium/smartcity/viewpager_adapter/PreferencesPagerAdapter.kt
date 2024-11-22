package com.elgenium.smartcity.viewpager_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R

class PreferencesPagerAdapter : RecyclerView.Adapter<PreferencesPagerAdapter.ViewHolder>() {

    private val layouts = listOf(
        R.layout.layout_daily_activities,  // Question 1
        R.layout.layout_places_to_visit,  // Question 2
        R.layout.layout_events            // Question 3
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layouts[viewType], parent, false)
        view.tag = "page_$viewType" // Tag each page for identification
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Nothing to bind since the layouts are static
    }

    override fun getItemCount(): Int = layouts.size

    override fun getItemViewType(position: Int): Int = position

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
