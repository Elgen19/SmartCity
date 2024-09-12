package com.elgenium.smartcity.singletons

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.util.Log
import com.elgenium.smartcity.R

object ActivityNavigationUtils {

    @Suppress("SameParameterValue")
    fun navigateToActivity(
        originActivity: Activity,
        destinationActivityClass: Class<*>,
        finishActivity: Boolean
    ) {
        val intent = Intent(originActivity, destinationActivityClass)
        val options = ActivityOptions.makeCustomAnimation(
            originActivity,
            R.anim.fade_in,
            R.anim.fade_out
        )
        originActivity.startActivity(intent, options.toBundle())

        if (finishActivity) {
            originActivity.finish()
            Log.d("Places Activity", "Activity terminated")
        }
    }
}
