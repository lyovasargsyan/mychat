package com.classchat.dto;

import com.classchat.model.ConversationType;
import java.time.Instant;
import java.util.List;

public record ConversationSummary(
        Long id,
        ConversationType type,
        String title,
        boolean owner,
        List<String> members,
        String lastMessage,
        Instant lastMessageAt,
        long unreadCount) {
}
