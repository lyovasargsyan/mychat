package com.classchat.service;

import com.classchat.dto.PushSubscriptionRequest;
import com.classchat.model.PushSubscription;
import com.classchat.model.UserAccount;
import com.classchat.repository.PushSubscriptionRepository;
import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {

    private static final Set<String> ALLOWED_PUSH_HOSTS = Set.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com",
            "web.push.apple.com");

    private final PushSubscriptionRepository subscriptions;
    private final Clock clock;

    public PushSubscriptionService(PushSubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Transactional
    public void save(UserAccount user, PushSubscriptionRequest request) {
        validateEndpoint(request.endpoint());
        PushSubscription subscription = subscriptions.findByEndpoint(request.endpoint()).orElse(null);
        if (subscription == null) {
            subscriptions.save(new PushSubscription(user, request.endpoint(),
                    request.keys().p256dh(), request.keys().auth(), clock.instant()));
        } else {
            subscription.update(user, request.keys().p256dh(), request.keys().auth(), clock.instant());
        }
    }

    @Transactional
    public void remove(UserAccount user, String endpoint) {
        validateEndpoint(endpoint);
        subscriptions.deleteByEndpointAndUserId(endpoint, user.getId());
    }

    private void validateEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || !ALLOWED_PUSH_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getPath() == null
                    || uri.getPath().isBlank()) {
                throw invalidEndpoint();
            }
        } catch (IllegalArgumentException exception) {
            throw invalidEndpoint();
        }
    }

    private ApiException invalidEndpoint() {
        return new ApiException(HttpStatus.BAD_REQUEST, "The push endpoint is invalid or unsupported.");
    }
}
