package com.opdehipt.native_push

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.initialize
import com.google.firebase.messaging.FirebaseMessaging
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Plugin class for handling native push notifications in a Flutter application.
 */
class NativePushPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {

  companion object {
    private const val TAG = "NativePushPlugin"
    private var channel: MethodChannel? = null

    /**
     * Passes the new notification token to the Flutter side.
     *
     * @param token The new FCM token.
     */
    internal fun newToken(token: String) {
      Handler(Looper.getMainLooper()).post {
        channel?.invokeMethod("newNotificationToken", token)
      }
    }

    /**
     * Parses the notification data from the given intent and sends it to the Flutter side.
     *
     * @param intent The intent containing notification data.
     */
    private fun newNotification(intent: Intent) {
      val data = parseNotification(intent) ?: return
      channel?.invokeMethod("newNotification", data)
    }

    /**
     * Parses the notification data from the given intent.
     *
     * @param intent The intent containing notification data.
     * @return A map containing the notification data.
     */
    private fun parseNotification(intent: Intent?): Map<String, String>? {
      val dataString = intent?.extras?.getString("native_push_data") ?: return null
      val data = mutableMapOf<String, String>()
      val jsonObject = JSONObject(dataString)
      val jsonKeys = jsonObject.keys()
      for (key in jsonKeys) {
        val value = jsonObject.getString(key)
        data[key] = value
      }
      return data
    }
  }

  // The MethodChannel that will facilitate communication between Flutter and native Android
  private lateinit var channel: MethodChannel
  private lateinit var context: Context
  private var activity: Activity? = null

  /**
   * Called when the plugin is attached to the Flutter engine.
   *
   * @param flutterPluginBinding The binding that provides the Flutter engine context.
   */
  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "com.opdehipt.native_push")
    channel.setMethodCallHandler(this)
    NativePushPlugin.channel = channel
  }

  /**
   * Called when a method is invoked on the MethodChannel.
   *
   * @param call The method call.
   * @param result The result of the method call.
   */
  override fun onMethodCall(call: MethodCall, result: Result) {
    CoroutineScope(Dispatchers.Main).launch {
      try {
        when (call.method) {
          "initialize" -> {
            initialize(call.arguments as Map<String, Any>)
            result.success(null)
          }
          "getInitialNotification" -> result.success(getInitialNotification())
          "registerForRemoteNotification" -> result.success(true)
          "getNotificationToken" -> result.success(getNotificationToken())
          "saveEncryptionKey" -> {
            val args = call.arguments as Map<*, *>
            val encryptionKey = args["encryptionKey"] as? String
            if (encryptionKey != null) {
              result.success(saveEncryptionKey(encryptionKey))
            } else {
              result.error("INVALID_ARGUMENT", "encryptionKey is required", null)
            }
          }
          else -> result.notImplemented()
        }
      }
      catch (e: Exception) {
        result.error("native_push_error", null, e)
      }
    }
  }

  /**
   * Called when the plugin is detached from the Flutter engine.
   *
   * @param binding The binding that provided the Flutter engine context.
   */
  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
  }

  /**
   * Called when the plugin is attached to an activity.
   *
   * @param binding The binding that provides the activity context.
   */
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addOnNewIntentListener {
      newNotification(it)
      false
    }
  }

  /**
   * Called when the plugin is reattached to an activity for configuration changes.
   *
   * @param binding The binding that provides the activity context.
   */
  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  /**
   * Called when the plugin is detached from an activity.
   */
  override fun onDetachedFromActivity() {
    activity = null
  }

  /**
   * Called when the plugin is detached from an activity for configuration changes.
   */
  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
    NativePushPlugin.channel = null
  }

  /**
   * Initializes the plugin with the provided parameters.
   *
   * @param params The initialization parameters.
   */
  private suspend fun initialize(params: Map<String, Any>) {
    withContext(Dispatchers.Main) {
      if (FirebaseApp.getApps(context).isEmpty()) {
        val firebaseOptions = params["firebaseOptions"] as? Map<*, *>
        if (firebaseOptions == null) {
          Log.e(TAG, "firebaseOptions is not Map")
          throw IllegalArgumentException()
        }
        val projectId = firebaseOptions["projectId"] as? String
        val applicationId = firebaseOptions["applicationId"] as? String
        val apiKey = firebaseOptions["apiKey"] as? String
        if (projectId == null) {
          Log.e(TAG, "projectId is not String")
          throw IllegalArgumentException()
        }
        if (applicationId == null) {
          Log.e(TAG, "applicationId is not String")
          throw IllegalArgumentException()
        }
        if (apiKey == null) {
          Log.e(TAG, "apiKey is not String")
          throw IllegalArgumentException()
        }
        val options = FirebaseOptions.Builder()
          .setProjectId(projectId)
          .setApplicationId(applicationId)
          .setApiKey(apiKey)
          .build()
        Firebase.initialize(context, options)
      }
    }
  }

  /**
   * Retrieves the initial notification data if available.
   *
   * @return A map containing the initial notification data.
   */
  private fun getInitialNotification(): Map<String, String>? = parseNotification(activity?.intent)

  /**
   * Retrieves the current FCM notification token.
   *
   * @return The current FCM token.
   */
  private suspend fun getNotificationToken(): String? =
    try {
      withContext(Dispatchers.IO) {
        FirebaseMessaging.getInstance().token.await()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to get FCM token", e)
      null
    }

  /**
   * Saves the encryption key to a file in the app's files directory.
   *
   * @param encryptionKey Base64-encoded encryption key.
   * @return true if the key was saved successfully.
   */
  private fun saveEncryptionKey(encryptionKey: String): Boolean {
    return try {
      val file = java.io.File(context.filesDir, "native_push_encryption_key.txt")
      file.writeText(encryptionKey)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save encryption key", e)
      false
    }
  }
}
