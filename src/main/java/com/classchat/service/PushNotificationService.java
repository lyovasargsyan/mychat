package com.classchat.service;

import com.classchat.config.PushProperties;
import com.classchat.model.ConversationMember;
import com.classchat.model.PushSubscription;
import com.classchat.repository.ConversationMemberRepository;
import com.classchat.repository.PushSubscriptionRepository;
import com.interaso.webpush.WebPush;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushProperties pushProperties;
    private final ConversationMemberRepository members;
    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PushNotificationService(PushProperties pushProperties,
                                   ConversationMemberRepository members,
                                   PushSubscriptionRepository subscriptions,
                                   ObjectMapper objectMapper) {
        this.pushProperties = pushProperties;
        this.members = members;
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    }

    @Async("pushTaskExecutor")
    @Transactional
    public void notifyConversationMembers(Long conversationId, Long senderId, String senderHandle,
                                          String content, Long messageId) {
        if (!pushProperties.isEnabled()) {
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "title", "New message from @" + senderHandle,
                    "body", content,
                    "conversationId", conversationId,
                    "messageId", messageId,
                    "url", "/?conversationId=" + conversationId));
        } catch (JacksonException exception) {
            log.error("Could not encode a chat push notification", exception);
            return;
        }

        for (ConversationMember member : members.findAllByConversationIdOrderByJoinedAtAsc(conversationId)) {
            Long recipientId = member.getUser().getId();
            if (recipientId.equals(senderId)) {
                continue;
            }
            for (PushSubscription subscription : subscriptions.findAllByUserId(recipientId)) {
                send(subscription, payload);
            }
        }
    }

    private void send(PushSubscription subscription, String payload) {
        try {
            WebPush webPush = pushProperties.getWebPush();
            byte[] body = webPush.getBody(payload.getBytes(StandardCharsets.UTF_8),
                    Base64.getUrlDecoder().decode(subscription.getP256dh()),
                    Base64.getUrlDecoder().decode(subscription.getAuth()));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(subscription.getEndpoint()))
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            webPush.getHeaders(subscription.getEndpoint(), 60, null, null)
                    .forEach(request::header);

            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 410) {
                subscriptions.delete(subscription);
            } else if (response.statusCode() != 200
                    && response.statusCode() != 201
                    && response.statusCode() != 202) {
                log.warn("Web Push service returned status {} for subscription {}",
                        response.statusCode(), subscription.getId());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Web Push delivery was interrupted for subscription {}", subscription.getId());
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Could not deliver a Web Push notification for subscription {}: {}",
                    subscription.getId(), exception.getClass().getSimpleName());
        }
    }
}
