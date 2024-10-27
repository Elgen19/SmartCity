package com.elgenium.smartcity.work_managers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elgenium.smartcity.intelligence.ReportVerifier
import com.elgenium.smartcity.models.Event
import com.google.firebase.database.FirebaseDatabase

class VerifyEventsWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val reportVerifier = ReportVerifier()  // Instantiate ReportVerifier
    private val context = appContext

    override suspend fun doWork(): Result {
        FirebaseDatabase.getInstance().getReference("Events")
        return try {
            // Fetch events from Firebase
            val events: List<Event> = reportVerifier.fetchEventsFromFirebase()

            for (event in events) {
                reportVerifier.verifyAndUpdateEvent(context, event) // Verify each event

                // Handle notifications based on verification result
                val statusMessage = if (event.checker != null) {
                    "Event ${event.eventName} verified and status updated."
                } else {
                    "Event verification failed: Missing checker for ${event.eventName}."
                }
                // Log or send notification (implement sendNotification method as needed)
                Log.e("VerifyEventsWorker", statusMessage)
            }

            Result.success() // Return success
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure() // Return failure in case of an exception
        }
    }
}
