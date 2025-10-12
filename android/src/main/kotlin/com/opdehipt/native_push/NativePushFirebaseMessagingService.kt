package com.opdehipt.native_push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION_CODES
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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

        val channel = message.data["channel"]
        if (channel == null) {
            logError("Notification channel not found")
            return
        }

        // Decrypt the notification payload
        val encryptedData = message.data["encrypted"]
        val nonce = message.data["nonce"]

        val (title, body) = if (encryptedData != null && nonce != null) {
            try {
                decryptNotification(encryptedData, nonce)
            } catch (e: Exception) {
                logError("Failed to decrypt notification: ${e.message}")
                showDecryptionErrorNotification(notificationManager, id, channel)
                return
            }
        } else {
            logError("Encrypted data or nonce not found")
            showDecryptionErrorNotification(notificationManager, id, channel)
            return
        }

        val notificationBuilder = NotificationCompat.Builder(
            this,
            channel,
        )
            .setAutoCancel(true)

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            logError("Intent is null")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra("native_push_data", JSONObject(message.data as Map<*, *>).toString())
        val requestCode = if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            intent.identifier = message.messageId ?: UUID.randomUUID().toString()
            0
        } else {
            (message.messageId ?: UUID.randomUUID().toString()).hashCode()
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        notificationBuilder.setContentIntent(pendingIntent)

        notificationBuilder.setContentTitle(title)
        body?.let { notificationBuilder.setContentText(it) }

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

    /**
     * Decrypts the notification payload using AES-GCM.
     *
     * @param encryptedData Base64-encoded encrypted JSON string containing title and body.
     * @param nonceBase64 Base64-encoded nonce for AES-GCM decryption.
     * @return Pair of title and optional body from the decrypted JSON.
     */
    private fun decryptNotification(encryptedData: String, nonceBase64: String): Pair<String, String?> {
        // Read encryption key from file
        val keyFile = File(filesDir, "native_push_encryption_key.txt")
        if (!keyFile.exists()) {
            throw IllegalStateException("Encryption key file not found: ${keyFile.absolutePath}")
        }
        val keyBase64 = keyFile.readText().trim()

        // Decode Base64 values
        val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
        val nonce = Base64.decode(nonceBase64, Base64.DEFAULT)
        val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)

        // Initialize AES-GCM cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val gcmParameterSpec = GCMParameterSpec(128, nonce) // 128-bit authentication tag
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

        // Decrypt the data
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        val decryptedJson = String(decryptedBytes, Charsets.UTF_8)

        // Parse the JSON
        val jsonObject = JSONObject(decryptedJson)
        val title = jsonObject.getString("title")
        val body = jsonObject.optString("body", null)

        return Pair(title, body)
    }

    /**
     * Shows an error notification when decryption fails.
     *
     * @param notificationManager The notification manager.
     * @param id The notification ID.
     * @param channel The notification channel.
     */
    private fun showDecryptionErrorNotification(
        notificationManager: NotificationManager?,
        id: Int,
        channel: String
    ) {
        val notificationBuilder = NotificationCompat.Builder(this, channel)
            .setAutoCancel(true)
            .setContentTitle("Notification decrypting failed")

        // Set notification icon
        val metadataInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val iconResource = metadataInfo.metaData.getInt("com.google.firebase.messaging.default_notification_icon")
        if (iconResource == ResourcesCompat.ID_NULL) {
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
