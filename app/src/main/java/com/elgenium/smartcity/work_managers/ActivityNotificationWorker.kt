package com.elgenium.smartcity.work_managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.elgenium.smartcity.ActiveActivitiesActivity
import com.elgenium.smartcity.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ActivityNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "ActivityPlannerChannel"
        const val CHANNEL_NAME = "Activity Planner Notifications"
        const val CHANNEL_DESCRIPTION = "Notifications for scheduled activities in the SmartCity app"
    }
    private var nameOfActivity = ""
    private var status = ""

    override fun doWork(): Result {
        // Get data passed to the Worker
        val activityName = inputData.getString("activityName") ?: return Result.failure()
        nameOfActivity = activityName
        val notificationMessage = inputData.getString("notificationMessage") ?: return Result.failure()
        val containerId = inputData.getString("containerId")
        val activityId = inputData.getString("activityId")
        val newStatus = inputData.getString("newStatus") ?: "NO STATUS" // You can pass "Finished" or other status values
        status = newStatus

        // Show notification
        showNotification(activityName, notificationMessage)

        // If activityId and containerId are available, update the status in Firebase
        if (!activityId.isNullOrEmpty() && !containerId.isNullOrEmpty() && !newStatus.isNullOrEmpty()) {
            updateStatusInFirebase(containerId, activityId, newStatus)
        }

        return Result.success()
    }

    private fun showNotification(activityName: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure the notification channel is created
        createNotificationChannel(notificationManager)

        // Create an Intent to redirect to ActiveActivitiesActivity
        val intent = Intent(context, ActiveActivitiesActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        // Create a PendingIntent to handle the redirect when the notification is tapped
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(activityName)
            .setContentText(message)
            .setSmallIcon(R.drawable.smart_city_logo) // Use your app's notification icon
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Heads-up notification
            .setAutoCancel(true) // Automatically dismiss notification when tapped
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE) // Default sound and vibration
            .setVibrate(longArrayOf(0, 500, 1000)) // Custom vibration pattern (optional)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)) // Set a custom sound (optional)
            .setContentIntent(pendingIntent) // Set the PendingIntent for activity redirection
            .build()

        // Notify the user
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 1000)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateStatusInFirebase(containerId: String, activityId: String?, newStatus: String) {
        if (activityId.isNullOrEmpty()) {
            Log.e("NotificationScheduler", "Activity ID is null or empty, cannot update status.")
            return
        }

        // Get the current user ID from Firebase Authentication
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid

        if (userId.isNullOrEmpty()) {
            Log.e("NotificationScheduler", "User ID is null or user is not authenticated.")
            return
        }

        // Build the path to the specific activity in Firebase
        val activityRef = FirebaseDatabase.getInstance().reference
            .child("Users")
            .child(userId)
            .child("MyActivities")
            .child(containerId)
            .child("activities")
            .child(activityId)

        // Update the status field in Firebase
        activityRef.child("status")
            .setValue(newStatus)
            .addOnSuccessListener {
                Log.d("NotificationScheduler", "Successfully updated status for activity ID: $nameOfActivity with $status")
            }
            .addOnFailureListener { e ->
                Log.e("NotificationScheduler", "Failed to update status for activity ID: $activityId", e)
            }
    }
}
