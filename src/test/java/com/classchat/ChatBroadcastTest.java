package com.classchat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.classchat.dto.ChatSendRequest;
import com.classchat.dto.ConversationMessage;
import com.classchat.model.UserAccount;
import com.classchat.repository.UserAccountRepository;
import com.classchat.service.ConversationService;
import com.classchat.TestMailConfiguration.RecordingVerificationEmailSender;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.WebSocketHttpHeaders;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestMailConfiguration.class)
class ChatBroadcastTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private SimpUserRegistry userRegistry;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private ConversationService conversations;

    @Autowired
    private RecordingVerificationEmailSender verificationEmails;

    @Test
    void authenticatedConversationMembersExchangePersistedMessages() throws Exception {
        Map<String, String> anaCookies = register("ana@example.test", "ana");
        Map<String, String> benCookies = register("ben@example.test", "ben");
        login(anaCookies, "ana@example.test", "ana");
        login(benCookies, "@ben", "ben");
        UserAccount ana = users.findByHandleIgnoreCase("ana").orElseThrow();
        var conversation = conversations.openDirect(ana, "@ben");
        BlockingQueue<ConversationMessage> anaInbox = new LinkedBlockingQueue<>();
        BlockingQueue<ConversationMessage> benInbox = new LinkedBlockingQueue<>();
        StompSession anaSession = connectAndSubscribe(anaCookies, conversation.id(), anaInbox);
        StompSession benSession = connectAndSubscribe(benCookies, conversation.id(), benInbox);
        waitUntilSubscribed(2, conversation.id());

        anaSession.send("/app/conversations/" + conversation.id() + "/send",
                new ChatSendRequest("Hello from Ana"));

        ConversationMessage receivedByBen = benInbox.poll(5, TimeUnit.SECONDS);
        ConversationMessage receivedByAna = anaInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(receivedByBen, "Ben should receive a message in his direct conversation");
        assertEquals("@ana", receivedByBen.sender());
        assertEquals("Hello from Ana", receivedByBen.content());
        assertNotNull(receivedByAna, "Ana should receive her own message too");
        assertEquals(receivedByBen.id(), receivedByAna.id());

        anaSession.disconnect();
        benSession.disconnect();
    }

    private Map<String, String> register(String email, String handle) {
        RestClient rest = RestClient.create();
        Map<String, String> cookies = new LinkedHashMap<>();
        ResponseEntity<Map> csrfResponse = rest.get()
                .uri(baseUrl() + "/api/auth/csrf")
                .retrieve()
                .toEntity(Map.class);
        rememberCookies(csrfResponse.getHeaders(), cookies);
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders headers = sessionHeaders(cookies, csrfToken);
        Map<String, String> body = Map.of("email", email, "handle", handle, "password", "test-password-123");
        ResponseEntity<Map> response = rest.post()
                .uri(baseUrl() + "/api/auth/register")
                .headers(requestHeaders -> requestHeaders.addAll(headers))
                .body(body)
                .retrieve()
                .toEntity(Map.class);
        rememberCookies(response.getHeaders(), cookies);
        assertEquals(email, response.getBody().get("email"));
        String verificationCode = verificationEmails.codeFor(email);
        ResponseEntity<Map> verifyCsrfResponse = rest.get()
                .uri(baseUrl() + "/api/auth/csrf")
                .header(HttpHeaders.COOKIE, cookieHeader(cookies))
                .retrieve()
                .toEntity(Map.class);
        rememberCookies(verifyCsrfResponse.getHeaders(), cookies);
        HttpHeaders verifyHeaders = sessionHeaders(cookies, (String) verifyCsrfResponse.getBody().get("token"));
        int unverifiedLoginStatus = rest.post()
                .uri(baseUrl() + "/api/auth/login")
                .headers(requestHeaders -> requestHeaders.addAll(verifyHeaders))
                .body(Map.of("identity", email, "password", "test-password-123"))
                .exchange((request, exchangeResponse) -> exchangeResponse.getStatusCode().value());
        assertEquals(403, unverifiedLoginStatus);
        ResponseEntity<Map> verified = rest.post()
                .uri(baseUrl() + "/api/auth/verify-email")
                .headers(requestHeaders -> requestHeaders.addAll(verifyHeaders))
                .body(Map.of("email", email, "code", verificationCode))
                .retrieve()
                .toEntity(Map.class);
        assertEquals("Email verified. Your account is ready.", verified.getBody().get("message"));
        return cookies;
    }

    private void login(Map<String, String> cookies, String identity, String expectedHandle) {
        RestClient rest = RestClient.create();
        ResponseEntity<Map> csrfResponse = rest.get()
                .uri(baseUrl() + "/api/auth/csrf")
                .header(HttpHeaders.COOKIE, cookieHeader(cookies))
                .retrieve()
                .toEntity(Map.class);
        rememberCookies(csrfResponse.getHeaders(), cookies);
        String csrfToken = (String) csrfResponse.getBody().get("token");

        HttpHeaders headers = sessionHeaders(cookies, csrfToken);
        Map<String, String> body = Map.of("identity", identity, "password", "test-password-123");
        ResponseEntity<Map> response = rest.post()
                .uri(baseUrl() + "/api/auth/login")
                .headers(requestHeaders -> requestHeaders.addAll(headers))
                .body(body)
                .retrieve()
                .toEntity(Map.class);
        rememberCookies(response.getHeaders(), cookies);
        assertEquals(expectedHandle, response.getBody().get("handle"));
        ResponseEntity<Map> currentUser = rest.get()
                .uri(baseUrl() + "/api/auth/me")
                .header(HttpHeaders.COOKIE, cookieHeader(cookies))
                .retrieve()
                .toEntity(Map.class);
        assertEquals(expectedHandle, currentUser.getBody().get("handle"));
    }

    private StompSession connectAndSubscribe(Map<String, String> cookies, Long conversationId,
                                             BlockingQueue<ConversationMessage> inbox) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookieHeader(cookies));
        StompSession session = client.connectAsync(
                        URI.create("ws://localhost:" + port + "/ws"),
                        headers,
                        new StompHeaders(),
                        new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
        session.subscribe("/user/queue/conversations/" + conversationId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ConversationMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((ConversationMessage) payload);
            }
        });
        return session;
    }

    private HttpHeaders sessionHeaders(Map<String, String> cookies, String csrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE, cookieHeader(cookies));
        headers.set("X-XSRF-TOKEN", csrfToken);
        return headers;
    }

    private void rememberCookies(HttpHeaders headers, Map<String, String> cookies) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) {
            return;
        }
        for (String cookie : setCookies) {
            String pair = cookie.split(";", 2)[0];
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                cookies.put(parts[0], parts[1]);
            }
        }
    }

    private String cookieHeader(Map<String, String> cookies) {
        List<String> pairs = new ArrayList<>();
        cookies.forEach((name, value) -> pairs.add(name + "=" + value));
        return String.join("; ", pairs);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private void waitUntilSubscribed(int expected, Long conversationId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (countSubscribers(conversationId) < expected) {
            if (System.nanoTime() > deadline) {
                fail("the subscriptions were not registered in time: " + subscriptionDestinations());
            }
            Thread.sleep(20);
        }
    }

    private String subscriptionDestinations() {
        return userRegistry.getUsers().stream()
                .flatMap(user -> user.getSessions().stream())
                .flatMap(session -> session.getSubscriptions().stream())
                .map(subscription -> subscription.getDestination())
                .collect(Collectors.joining(", "));
    }

    private long countSubscribers(Long conversationId) {
        String destinationPart = "/queue/conversations/" + conversationId;
        return userRegistry.getUsers().stream()
                .flatMap(user -> user.getSessions().stream())
                .flatMap(session -> session.getSubscriptions().stream())
                .filter(subscription -> subscription.getDestination().contains(destinationPart))
                .count();
    }
}
