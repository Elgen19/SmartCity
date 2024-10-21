package com.elgenium.smartcity.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.elgenium.smartcity.NotificationHistoryActivity
import com.elgenium.smartcity.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushNotificationsMessaging : FirebaseMessagingService() {
    private  var isWeatherNotificationEnabled = false
    private var isMealNotificationEnabled = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.e("FCM", "From: ${remoteMessage.from}")

        // Retrieve user preferences for notifications
        retrievePreferences()

        remoteMessage.notification?.let {
            Log.e("FCM", "Message: ${it.body}")

            // Determine the appropriate channel ID based on the notification type
            val channelId = when (it.title) {
                "Snack Recommendation" -> {
                    if (!isMealNotificationEnabled) return  // Exit if meal notifications are disabled
                    "SNACK_CHANNEL"
                }
                "Lunch Recommendation" -> {
                    if (!isMealNotificationEnabled) return  // Exit if meal notifications are disabled
                    "LUNCH_CHANNEL"
                }
                "Dinner Recommendation" -> {
                    if (!isMealNotificationEnabled) return  // Exit if meal notifications are disabled
                    "DINNER_CHANNEL"
                }
                "Breakfast Recommendation" -> {
                    if (!isMealNotificationEnabled) return  // Exit if meal notifications are disabled
                    "BREAKFAST_CHANNEL"
                }
                else -> {
                    if (!isWeatherNotificationEnabled) return  // Exit if weather notifications are disabled
                    "WEATHER_CHANNEL"
                }
            }

            // Send the notification only if the corresponding notification type is enabled
            sendNotification(it.body ?: "", channelId) // Pass the channel ID
        }
    }


    private fun retrievePreferences() {
        val sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)
        isWeatherNotificationEnabled = sharedPreferences.getBoolean("weather_notifications", false)
        isMealNotificationEnabled = sharedPreferences.getBoolean("meal_notifications", false)

        // Optionally log the retrieved value
        Log.e("Preferences", "weather notif value: $isWeatherNotificationEnabled")
        Log.e("Preferences", "meal notif value: $isMealNotificationEnabled")


    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.e("FCM", "New token: $token")

    }


    private fun sendNotification(messageBody: String, channelId: String) {
        val intent = Intent(this, NotificationHistoryActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.smart_city_logo)
            .setContentTitle("Notification") // You can customize the title based on the message type
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(0, notificationBuilder.build())
    }


    private fun createNotificationChannel(channelId: String, channelName: String, channelDescription: String) {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = channelDescription
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotificationChannels() {
        // Create Weather Notifications Channel
        createNotificationChannel("WEATHER_CHANNEL", "Weather Notifications", "Channel for weather notifications")

        // Create Snack Recommendations Channel
        createNotificationChannel("SNACK_CHANNEL", "Snack Recommendations", "Channel for snack recommendations")

        // Create Lunch Notifications Channel
        createNotificationChannel("LUNCH_CHANNEL", "Lunch Recommendations", "Channel for lunch recommendations")

        // Create Dinner Notifications Channel
        createNotificationChannel("DINNER_CHANNEL", "Dinner Recommendations", "Channel for dinner recommendations")

        // Create Breakfast Notifications Channel
        createNotificationChannel("BREAKFAST_CHANNEL", "Breakfast Recommendations", "Channel for breakfast recommendations")
    }


}

