package com.classchat.service;

import com.classchat.dto.ConversationMessage;
import com.classchat.dto.ConversationSummary;
import com.classchat.dto.CreateGroupRequest;
import com.classchat.model.Conversation;
import com.classchat.model.ConversationMember;
import com.classchat.model.ConversationType;
import com.classchat.model.StoredMessage;
import com.classchat.model.UserAccount;
import com.classchat.repository.ConversationMemberRepository;
import com.classchat.repository.ConversationRepository;
import com.classchat.repository.StoredMessageRepository;
import com.classchat.repository.UserAccountRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_LIMIT = 100;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final StoredMessageRepository messages;
    private final UserAccountRepository users;
    private final Clock clock;

    public ConversationService(ConversationRepository conversations,
                               ConversationMemberRepository members,
                               StoredMessageRepository messages,
                               UserAccountRepository users,
                               Clock clock) {
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(UserAccount currentUser) {
        return conversations.findAllForUser(currentUser.getId()).stream()
                .map(conversation -> summary(conversation, currentUser))
                .toList();
    }

    @Transactional
    public ConversationSummary openDirect(UserAccount currentUser, String requestedHandle) {
        UserAccount other = requireUser(requestedHandle);
        if (other.getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot start a conversation with yourself.");
        }

        List<Conversation> existing = conversations.findDirectBetween(currentUser.getId(), other.getId());
        if (!existing.isEmpty()) {
            return summary(existing.get(0), currentUser);
        }

        Conversation conversation = conversations.save(
                new Conversation(ConversationType.DIRECT, null, currentUser, clock.instant()));
        addMember(conversation, currentUser);
        addMember(conversation, other);
        return summary(conversation, currentUser);
    }

    @Transactional
    public ConversationSummary createGroup(UserAccount owner, CreateGroupRequest request) {
        List<String> requestedHandles = request.handles() == null ? List.of() : request.handles();
        Map<Long, UserAccount> invitedUsers = new LinkedHashMap<>();
        for (String handle : requestedHandles) {
            UserAccount user = requireUser(handle);
            if (user.getId().equals(owner.getId())) {
                continue;
            }
            invitedUsers.put(user.getId(), user);
        }
        if (invitedUsers.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Add at least one other member to the group.");
        }

        Conversation conversation = conversations.save(
                new Conversation(ConversationType.GROUP, request.title().trim(), owner, clock.instant()));
        addMember(conversation, owner);
        invitedUsers.values().forEach(user -> addMember(conversation, user));
        return summary(conversation, owner);
    }

    @Transactional
    public ConversationSummary addGroupMember(UserAccount currentUser, Long conversationId, String handle) {
        Conversation conversation = requireGroupOwner(currentUser, conversationId);
        UserAccount newMember = requireUser(handle);
        if (!members.existsByConversationIdAndUserId(conversationId, newMember.getId())) {
            addMember(conversation, newMember);
        }
        return summary(conversation, currentUser);
    }

    @Transactional
    public ConversationSummary removeGroupMember(UserAccount currentUser, Long conversationId, String handle) {
        Conversation conversation = requireGroupOwner(currentUser, conversationId);
        UserAccount member = requireUser(handle);
        if (member.getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The group creator cannot remove themselves.");
        }
        if (!members.existsByConversationIdAndUserId(conversationId, member.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "That user is not a member of this group.");
        }
        members.deleteByConversationIdAndUserId(conversationId, member.getId());
        return summary(conversation, currentUser);
    }

    @Transactional(readOnly = true)
    public List<ConversationMessage> history(UserAccount currentUser, Long conversationId, Long beforeId, int limit) {
        requireMembership(currentUser, conversationId);
        int boundedLimit = Math.max(1, Math.min(limit, MAX_MESSAGE_LIMIT));
        PageRequest page = PageRequest.of(0, boundedLimit);
        List<StoredMessage> rows = beforeId == null
                ? messages.findByConversationIdOrderByIdDesc(conversationId, page)
                : messages.findByConversationIdAndIdLessThanOrderByIdDesc(conversationId, beforeId, page);
        List<ConversationMessage> result = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            result.add(toMessage(rows.get(i)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<String> memberHandles(Long conversationId) {
        return members.findAllByConversationIdOrderByJoinedAtAsc(conversationId).stream()
                .map(member -> member.getUser().getHandle())
                .toList();
    }

    @Transactional
    public ConversationMessage storeMessage(Long conversationId, String handle, String content) {
        UserAccount sender = requireUser(handle);
        Conversation conversation = requireMembership(sender, conversationId);
        if (content == null || content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Message must not be blank.");
        }
        if (content.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Message must be 500 characters or fewer.");
        }
        StoredMessage stored = messages.save(new StoredMessage(conversation, sender, content, clock.instant()));
        return toMessage(stored);
    }

    @Transactional
    public ConversationSummary markRead(UserAccount currentUser, Long conversationId) {
        requireMembership(currentUser, conversationId);
        ConversationMember member = members.findByConversationIdAndUserId(conversationId, currentUser.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "You are not a member of this conversation."));
        List<StoredMessage> latest = messages.findTop1ByConversationIdOrderByIdDesc(conversationId);
        if (!latest.isEmpty()) {
            member.markReadThrough(latest.get(0).getId());
        }
        return summary(conversations.findById(conversationId).orElseThrow(), currentUser);
    }

    private ConversationSummary summary(Conversation conversation, UserAccount currentUser) {
        List<String> handles = members.findAllByConversationIdOrderByJoinedAtAsc(conversation.getId()).stream()
                .map(member -> "@" + member.getUser().getHandle())
                .toList();
        List<StoredMessage> latest = messages.findTop1ByConversationIdOrderByIdDesc(conversation.getId());
        StoredMessage last = latest.isEmpty() ? null : latest.get(0);
        ConversationMember membership = members.findByConversationIdAndUserId(conversation.getId(), currentUser.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "You are not a member of this conversation."));
        long unreadCount = messages.countByConversationIdAndIdGreaterThanAndSenderIdNot(
                conversation.getId(),
                membership.getLastReadMessageId() == null ? 0L : membership.getLastReadMessageId(),
                currentUser.getId());
        String title = conversation.getType() == ConversationType.GROUP
                ? conversation.getTitle()
                : handles.stream()
                        .filter(handle -> !handle.equalsIgnoreCase("@" + currentUser.getHandle()))
                        .findFirst()
                        .orElse("Direct message");
        return new ConversationSummary(conversation.getId(), conversation.getType(), title,
                conversation.getOwner().getId().equals(currentUser.getId()), handles,
                last == null ? null : last.getContent(), last == null ? null : last.getCreatedAt(), unreadCount);
    }

    private Conversation requireGroupOwner(UserAccount currentUser, Long conversationId) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found."));
        if (conversation.getType() != ConversationType.GROUP) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This operation is only available for groups.");
        }
        if (!conversation.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the group creator can manage members.");
        }
        return conversation;
    }

    private Conversation requireMembership(UserAccount currentUser, Long conversationId) {
        if (!members.existsByConversationIdAndUserId(conversationId, currentUser.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not a member of this conversation.");
        }
        return conversations.findById(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found."));
    }

    private UserAccount requireUser(String requestedHandle) {
        String handle = AccountService.normalizeHandle(requestedHandle);
        return users.findByHandleIgnoreCase(handle)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User @" + handle + " was not found."));
    }

    private void addMember(Conversation conversation, UserAccount user) {
        ConversationMember member = new ConversationMember(conversation, user, clock.instant());
        List<StoredMessage> latest = messages.findTop1ByConversationIdOrderByIdDesc(conversation.getId());
        if (!latest.isEmpty()) {
            member.markReadThrough(latest.get(0).getId());
        }
        members.save(member);
    }

    private ConversationMessage toMessage(StoredMessage message) {
        return new ConversationMessage(message.getId(), message.getConversation().getId(),
                "@" + message.getSender().getHandle(), message.getContent(), message.getCreatedAt());
    }
}
