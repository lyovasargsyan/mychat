"use strict";

self.addEventListener("push", (event) => {
    let payload = {};
    if (event.data) {
        try {
            payload = event.data.json();
        } catch (error) {
            payload = { body: event.data.text() };
        }
    }

    event.waitUntil(self.registration.showNotification(payload.title || "New ClassChat message", {
        body: payload.body || "You have a new message.",
        data: { conversationId: payload.conversationId },
    }));
});

self.addEventListener("notificationclick", (event) => {
    event.notification.close();
    const conversationId = Number(event.notification.data && event.notification.data.conversationId);
    if (!Number.isSafeInteger(conversationId) || conversationId <= 0) {
        return;
    }

    event.waitUntil(clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
        for (const windowClient of windows) {
            if (new URL(windowClient.url).origin === self.location.origin) {
                windowClient.focus();
                windowClient.postMessage({ type: "open-conversation", conversationId });
                return;
            }
        }
        return clients.openWindow("/?conversationId=" + conversationId);
    }));
});
