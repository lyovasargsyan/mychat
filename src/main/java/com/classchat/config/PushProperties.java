package com.classchat.config;

import com.interaso.webpush.VapidKeys;
import com.interaso.webpush.WebPush;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PushProperties {

    private final WebPush webPush;
    private final String applicationServerKey;

    public PushProperties(@Value("${classchat.push.vapid-public-key:}") String publicKey,
                          @Value("${classchat.push.vapid-private-key:}") String privateKey,
                          @Value("${classchat.push.subject}") String subject) {
        if (publicKey.isBlank() && privateKey.isBlank()) {
            webPush = null;
            applicationServerKey = "";
        } else if (publicKey.isBlank() || privateKey.isBlank()) {
            throw new IllegalStateException("Set both VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY.");
        } else {
            try {
                VapidKeys vapidKeys = VapidKeys.fromUncompressedBytes(publicKey, privateKey);
                webPush = new WebPush(subject, vapidKeys);
                applicationServerKey = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(vapidKeys.getApplicationServerKey());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("The configured VAPID key pair or subject is invalid.", exception);
            }
        }
    }

    public boolean isEnabled() {
        return webPush != null;
    }

    public String getApplicationServerKey() {
        return applicationServerKey;
    }

    public WebPush getWebPush() {
        if (webPush == null) {
            throw new IllegalStateException("Web Push is not configured.");
        }
        return webPush;
    }
}
