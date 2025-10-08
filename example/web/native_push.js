let native_push_newNotificationCallback;
let native_push_abortController;

const url = new URL(location.href);
const base64InitialNotification = url.searchParams.get('native_push');
if (base64InitialNotification) {
    const padding = 4 - base64InitialNotification.length % 4;
    native_push_initialNotification = JSON.parse(atob(
        base64InitialNotification
            .replace("-", "+")
            .replace("_", '/')
            .padEnd(base64InitialNotification.length + (padding % 4), "=")
    ));
    url.searchParams.delete('native_push');
    history.replaceState(null, "", url.toString());
}

async function native_push_initializeRemoteNotification(newNotificationCallback) {
    native_push_newNotificationCallback = newNotificationCallback;
    if (!('serviceWorker' in navigator)) {
        throw new Error('Service workers are not supported');
    }
    if (!navigator.serviceWorker.controller) {
        const registration = await navigator.serviceWorker.getRegistration();
        if (!registration) {
            throw new Error('No service worker is registered');
        }
        // Wait for it to become active
        await navigator.serviceWorker.ready;
    }
    if (native_push_abortController) {
        native_push_abortController.abort();
    }
    native_push_abortController = new AbortController();
    navigator.serviceWorker.addEventListener('message', (event) => {
        switch (event.data?.type) {
            case "native_push_newNotification":
                if (native_push_newNotificationCallback) {
                    native_push_newNotificationCallback(event.data?.data);
                }
                break;
        }
    }, { signal: native_push_abortController.signal });
}

async function native_push_registerForRemoteNotification(vapidKey) {
    function base64ToArray(base64) {
        const binaryString = atob(base64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        return bytes;
    }

    const status = Notification.permission;
    if (status === 'granted') {
        const registration = await navigator.serviceWorker.ready;
        const subscription = await registration.pushManager.subscribe({
            userVisibleOnly: true,
            applicationServerKey: base64ToArray(vapidKey)
        });
        window.localStorage.setItem('native_push_token', JSON.stringify(subscription.toJSON()));
        return true;
    }
    else {
        return false;
    }
}
