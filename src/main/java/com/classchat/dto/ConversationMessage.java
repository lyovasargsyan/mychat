package com.classchat.dto;

import java.time.Instant;

public record ConversationMessage(Long id, Long conversationId, String sender, String content, Instant timestamp) {
}
