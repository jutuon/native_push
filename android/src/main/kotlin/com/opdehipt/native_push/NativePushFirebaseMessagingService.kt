package com.opdehipt.native_push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.util.UUID

/**
 * Service for handling Firebase push notifications.
 */
open class NativePushFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "NativePushService"
    }

    /**
     * Called when a new token for the default Firebase project is generated.
     *
     * @param token The new token.
     */
    override fun onNewToken(token: String) {
        NativePushPlugin.newToken(token)
    }

    /**
     * Called when a message is received from Firebase Cloud Messaging.
     *
     * @param message The received remote message.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager

        val id = message.data["id"]?.toIntOrNull()
        if (id == null) {
            logError("Notification ID not found")
            return
        }

        val title = message.data["title"]
        if (title == null) {
            notificationManager?.cancel(id)
            return
        }

        val channel = message.data["channel"]
        if (channel == null) {
            logError("Notification channel not found")
            return
        }

        val notificationBuilder = NotificationCompat.Builder(
            this,
            channel,
        )
            .setAutoCancel(true)

        // Define the intent that will be triggered when the user taps the notification
        val mainActivityClass = NativePushPlugin.mainActivityClass
        if (mainActivityClass != null) {
            val intent = Intent(this, mainActivityClass)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.putExtra("native_push_data", JSONObject(message.data as Map<*, *>).toString())
            intent.action = "com.opdehipt.native_push.PUSH"
            val requestCode = if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                intent.identifier = message.messageId ?: UUID.randomUUID().toString()
                0
            }
            else {
                (message.messageId ?: UUID.randomUUID().toString()).hashCode()
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )
            notificationBuilder.setContentIntent(pendingIntent)
        }

        notificationBuilder.setContentTitle(title)
        message.data["body"].let { notificationBuilder.setContentText(it) }

        // Retrieve application metadata to get default notification settings
        val metadataInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val iconResource = metadataInfo.metaData.getInt("com.google.firebase.messaging.default_notification_icon")
        if (iconResource == ResourcesCompat.ID_NULL) {
            // Fallback to launcher icon
            notificationBuilder.setSmallIcon(applicationInfo.icon)
        } else {
            notificationBuilder.setSmallIcon(iconResource)
        }

        notificationManager?.notify(id, notificationBuilder.build())
    }

    private fun logError(message: String) {
        // Include package name to log message as it is missing
        // from logcat when data message arrives when app process
        // is not running.
        Log.e(TAG, "$message, package: $packageName")
    }
}
