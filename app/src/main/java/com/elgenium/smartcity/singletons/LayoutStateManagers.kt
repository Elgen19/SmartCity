package com.elgenium.smartcity.singletons

import android.app.Activity
import android.widget.Button
import android.widget.TextView
import com.elgenium.smartcity.R

object LayoutStateManager {

    fun showLoadingLayout(activity: Activity, loadingText: String) {
        activity.setContentView(R.layout.status_saving_in_progress)
        activity.findViewById<TextView>(R.id.loading_text).text = loadingText
    }

    fun showSuccessLayout(activity: Activity, titleText: String, supportingText: String) {
        activity.setContentView(R.layout.status_success)
        activity.findViewById<TextView>(R.id.title_text).text = titleText
        activity.findViewById<TextView>(R.id.supporting_text).text = supportingText

        activity.findViewById<Button>(R.id.continue_button).setOnClickListener {
            activity.finish()
        }
    }

    fun showFailureLayout(activity: Activity, errorText: String, buttonText: String) {
        activity.setContentView(R.layout.status_failed_operation)
        activity.findViewById<TextView>(R.id.error_message).text = errorText
        activity.findViewById<Button>(R.id.return_button).text = buttonText

        activity.findViewById<Button>(R.id.return_button).setOnClickListener {
            activity.finish()
        }
    }

}
