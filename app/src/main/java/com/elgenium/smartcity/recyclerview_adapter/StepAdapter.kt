package com.elgenium.smartcity.recyclerview_adapter

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.Step

class StepAdapter(private val stepsList: List<Step>) : RecyclerView.Adapter<StepAdapter.StepViewHolder>() {

    class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val instructionTextView: TextView = itemView.findViewById(R.id.step_instruction)
        val distanceTextView: TextView = itemView.findViewById(R.id.step_distance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = stepsList[position]
        holder.instructionTextView.text = Html.fromHtml(step.instruction, Html.FROM_HTML_MODE_LEGACY)
        holder.distanceTextView.text = step.distance
    }

    override fun getItemCount() = stepsList.size
}
