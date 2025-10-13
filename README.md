# Native Push Plugin

The Native Push Plugin is a Flutter plugin that provides seamless integration of push notifications across different platforms including Android, iOS, and Web. This plugin allows your Flutter application to receive and handle remote notifications with ease.

## Features

- **End-to-end encrypted notifications** using AES-128-GCM encryption (Android
  and iOS).
- Supports Firebase Cloud Messaging (FCM) for Android.
- Supports Apple Push Notification Service (APNs) for iOS.
- Supports Web Push for web applications.
- Handles push notifications while the app is in the foreground, background, or terminated.
- Provides methods to initialize the plugin, register for remote notifications, and retrieve the notification token.
- Supports various notification options like alert, badge, sound, and more.

## Installation

Add the following dependency to your `pubspec.yaml` file:

```yaml
dependencies:
  native_push: ^1.0.0
```

Then run `flutter pub get` to install the plugin.

## Usage

### Import the Plugin

```dart
import 'package:native_push/native_push.dart';
```

### Initialize the Plugin

Before using the plugin, you need to initialize it. This is typically done in the `main.dart` file of your Flutter project.

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize the native push plugin
  await NativePush.instance.initialize(
    firebaseOptions: {
      'apiKey': 'YOUR_API_KEY',
      'projectId': 'YOUR_PROJECT_ID',
      'messagingSenderId': 'YOUR_MESSAGING_SENDER_ID',
      'applicationId': 'YOUR_APP_ID',
    },
  );

  runApp(MyApp());
}
```

The firebaseOptions can be omitted when not using fcm. You can use
the `extract_fcm_options` script provided in the repository to
extract the information from the `google-services.json`.

```bash
cat google-services.json | ./extract_fcm_options.sh <android-bundle-id>
```

### Save Encryption Key

Before registering for notifications, you must save the encryption key that will be used to decrypt notification payloads. The key must be a Base64-encoded AES-128 key (16 bytes).

```dart
import 'package:native_push/native_push.dart';

// Call this during app initialization
await NativePush.instance.saveEncryptionKey(
  'YOUR_BASE64_ENCODED_AES128_KEY',
  appGroupIdentifier: 'group.com.yourcompany.yourapp', // Required on iOS, ignored on Android
);
```

**Important for iOS**: The `appGroupIdentifier` parameter is required on iOS and
must match the App Group identifier configured in Xcode.
On Android, this parameter is ignored.

### Register for Remote Notifications

You need to register for remote notifications to get a notification token.

```dart
await NativePush.instance.registerForRemoteNotification(
  options: [],
  vapidKey: 'YOUR_VAPID_KEY', // For web push, can be omitted otherwise
);
```

### Notification Payload Format (Android and iOS)

All notifications must be sent as encrypted payloads. The notification data should include:

- `encrypted`: Base64-encoded encrypted JSON string containing `title` and optionally `body`
- `nonce`: Base64-encoded nonce for AES-GCM decryption
- `id`: Notification ID (unencrypted)
- `channel`: Notification channel (unencrypted, Android only)

The encryption uses AES-128-GCM with a 128-bit authentication tag.

### Handling Incoming Notifications

You can listen for incoming notifications using the `notificationStream`.

```dart
NativePush.instance.notificationStream.listen((notification) {
  // Handle the notification
  print('Received notification: $notification');
});
```

### Get Initial Notification

To handle the notification that opened the app, you can use `initialNotification`.

```dart
final initialNotification = await NativePush.instance.initialNotification();
if (initialNotification != null) {
  // Handle the initial notification
  print('Initial notification: $initialNotification');
}
```

### Retrieve the Notification Token

You can retrieve the current notification token with the following method:

```dart
final (service, token) = await NativePush.instance.notificationToken;
print('Notification Service: $service, Token: $token');
```

## Platform Specifics

### Android

For Android, ensure that you have add the following metadata to your application.

```xml
<meta-data
    android:name="com.google.firebase.messaging.default_notification_icon"
    android:resource="@android:drawable/ic_input_add" />
```

You need to create and specify your own notification channels for notifications.
Also enable notification permission using another library so that notifications
will be displayed.

### iOS

Like with
[flutter_local_notifications](https://github.com/MaikuB/flutter_local_notifications/tree/master/flutter_local_notifications#-ios-setup)
library, add the following to application method in AppDelegate.swift:

```
if #available(iOS 10.0, *) {
  UNUserNotificationCenter.current().delegate = self as? UNUserNotificationCenterDelegate
}
```

You have to add the `Push Notification` Capability. Also enable notification
permission using another library so that notifications will be displayed.

#### iOS Notification Service Extension Setup

**Step 1: Add Notification Service Extension**

1. Open your iOS project in Xcode (`ios/Runner.xcworkspace`)
2. File → New → Target → Notification Service Extension
3. Name it `NotificationService`
4. Set the language to Swift
5. Activate the scheme when prompted

**Step 2: Copy Template Code**

1. Copy the template from `darwin/Templates/NotificationService.swift` in this package
2. Replace the contents of your newly created extension's `NotificationService.swift` file with the template
3. **IMPORTANT**: Update the `APP_GROUP_IDENTIFIER` constant at the top of the file with your actual App Group identifier

```swift
// Example: Change this line in the template
private let APP_GROUP_IDENTIFIER = "group.com.example.app"
// To your actual App Group identifier:
private let APP_GROUP_IDENTIFIER = "group.com.yourcompany.yourapp"
```

**Step 3: Configure App Groups (Required for file sharing)**

1. In Xcode, select your main app target (Runner)
2. Go to "Signing & Capabilities" tab
3. Click "+ Capability" and add "App Groups"
4. Click the "+" button and create a new App Group identifier (e.g., `group.com.yourcompany.yourapp`)
5. Enable the checkbox next to your newly created App Group
6. Repeat steps 1-5 for the Notification Service Extension target
7. Make sure both targets use the **same App Group identifier**

### Web

Add
[native_push.js](https://github.com/Native-Push/native_push/blob/main/example/web/native_push.js)
to your web folder and the script to `index.html`.
Also enable notification permission using another library so
that registerForRemoteNotification function will work.

Your app must register a service worker which implements same features as
[native_push_sw.js](https://github.com/Native-Push/native_push/blob/main/example/web/native_push_sw.js).

## Example

An example Flutter app demonstrating the usage of the Native Push Plugin can be found in the `example` directory.

## Contributing

Contributions are welcome! Please submit a pull request or create an issue if you find a bug or have a feature request.

## License

This project is licensed under the BSD-3 License. See the
[LICENSE](LICENSE) file for details.
