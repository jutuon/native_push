"use strict";

// Event listener for when a push message is received
self.onpush = (event) => {
    // Destructure the incoming push message payload into separate variables
    let {
        title,                    // Notification title
        body,                     // Notification body text
        ...data     // Remaining data to be stored in the notification's data attribute
    } = event.data.json();        // Parse the incoming data as JSON

    const options = { data };
    if (body != null) options.body = body;

    // Ensure that the actions within are completed before the service worker terminates
    event.waitUntil(
        self.registration.showNotification(title, options)
    );
};

// Event listener for the installation of the service worker
self.oninstall = event => {
    // Ensures the new service worker activates immediately, bypassing the waiting state
    event.waitUntil(self.skipWaiting());
};

// Event listener for the activation of the service worker
self.onactivate = (event) => {
    // Ensures that the service worker takes control of all clients as soon as it activates
    event.waitUntil(self.clients.claim());
};

// Event listener for when a notification is clicked
self.onnotificationclick = (event) => {
    // Close the notification pop-up
    event.notification.close();

    // Ensure that the actions within are completed before the service worker terminates
    event.waitUntil(
        // Fetches all the clients (open windows) controlled by this service worker
        self.clients.matchAll({
            type: "window",
        })
        .then(async (clientList) => {
            const data = event.notification.data;
            if (data) {
                // If there are any open windows
                if (clientList.length !== 0) {
                    // Focus the first client window
                    const client = clientList[0];
                    await client.focus();
                    // Send a message to the client window with the notification data
                    client.postMessage({ type: "native_push_newNotification", data: data });
                }
                else {
                    // If no windows are open, create a base64 encoded URL fragment from the data
                    const dataBase64 = btoa(JSON.stringify(data))
                        .replace("+", '-')
                        .replace("/", '_')
                        .replace("=", '');
                    // Open a new window/tab with the URL containing the encoded data
                    await self.clients.openWindow(`/?native_push=${dataBase64}`);
                }
            }
        }),
    );
};
