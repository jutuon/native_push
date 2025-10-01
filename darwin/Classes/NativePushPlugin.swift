import Flutter
import UIKit
import UserNotifications

/// A plugin to handle native push notifications for Flutter applications.
public class NativePushPlugin: NSObject, FlutterPlugin, UNUserNotificationCenterDelegate {

    /// Registers the plugin with the Flutter registrar.
    /// - Parameter registrar: The Flutter plugin registrar.
    public static func register(with registrar: FlutterPluginRegistrar) {
        let messenger = registrar.messenger()
        let channel = FlutterMethodChannel(name: "com.opdehipt.native_push", binaryMessenger: messenger)
        let instance = NativePushPlugin(channel: channel)
        registrar.addMethodCallDelegate(instance, channel: channel)
        registrar.addApplicationDelegate(instance)
    }

    /// Handles the application finish launching event.
    /// - Parameters:
    ///   - application: The application instance.
    ///   - launchOptions: The launch options.
    /// - Returns: A boolean indicating successful launch.
    public func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [AnyHashable : Any] = [:]) -> Bool {
        if let notification = launchOptions[UIApplication.LaunchOptionsKey.remoteNotification] as? [AnyHashable: Any] {
            initialNotification = NativePushPlugin.transform(notification: notification)
        }
        return true
    }

    private let channel: FlutterMethodChannel
    private var initialNotification: [AnyHashable: Any]? = nil

    /// Initializes the plugin with a Flutter method channel.
    /// - Parameter channel: The Flutter method channel.
    init(channel: FlutterMethodChannel) {
        self.channel = channel
    }

    /// Handles method calls from Flutter.
    /// - Parameters:
    ///   - call: The Flutter method call.
    ///   - result: The result callback for the method call.
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        Task {
            do {
                switch call.method {
                case "initialize":
                    result("")
                case "getInitialNotification":
                    result(initialNotification)
                case "registerForRemoteNotification":
                    result(try await registerForRemoteNotification(call.arguments))
                case "getNotificationToken":
                    result(getNotificationToken())
                default:
                    result(FlutterMethodNotImplemented)
                }
            } catch {
                result(FlutterError(code: "native_push_error", message: nil, details: error))
            }
        }
    }

    /// Called when the application successfully registers for remote notifications.
    /// - Parameters:
    ///   - application: The application instance.
    ///   - deviceToken: The device token for push notifications.
    public func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { data in String(format: "%02.2hhx", data) }.joined()
        UserDefaults.standard.setValue(token, forKey: "native_push_remoteNotificationDeviceToken")
        Task {
            await MainActor.run {
                channel.invokeMethod("newNotificationToken", arguments: token)
            }
        }
    }

    public func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        NSLog("Failed to register for remote notifications: \(error)")
    }

    public func userNotificationCenter(_ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        if notification.request.content.userInfo["aps"] == nil {
            return;
        }
        if #available(iOS 14.0, *) {
          completionHandler([.list, .banner, .sound])
        } else {
          completionHandler([.alert, .sound])
        }
    }

    public func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        if response.notification.request.content.userInfo["aps"] == nil {
            return;
        }
        Task {
            await MainActor.run {
              channel.invokeMethod(
                "newNotification",
                arguments: NativePushPlugin.transform(notification: response.notification.request.content.userInfo)
              )
            }
        }
    }

    /// Transforms the notification content by removing unnecessary information.
    /// - Parameter notification: The notification content.
    /// - Returns: The transformed notification content.
    private static func transform(notification: [AnyHashable: Any]) -> [AnyHashable: Any] {
        var userInfo = notification
        userInfo.removeValue(forKey: "aps")
        return userInfo
    }

    /// Registers the application for remote notifications.
    /// - Parameter arguments: [String]
    /// - Returns: A boolean indicating successful registration.
    private func registerForRemoteNotification(_ arguments: Any?) async -> Bool {
        await UIApplication.shared.registerForRemoteNotifications()
        return true;
    }

    /// Retrieves the current notification token from user defaults.
    /// - Returns: The current notification token, if available.
    private func getNotificationToken() -> String? {
        UserDefaults.standard.string(forKey: "native_push_remoteNotificationDeviceToken")
    }
}
