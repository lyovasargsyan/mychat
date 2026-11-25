package com.classchat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: the whole Spring application (HTTP + WebSocket + STOMP broker) can start. */
@SpringBootTest
@ActiveProfiles("test")
class ClassChatApplicationTests {

    @Test
    void contextLoads() {
    }
}
