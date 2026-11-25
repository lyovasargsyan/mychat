package com.classchat.controller;

import com.classchat.dto.AddGroupMemberRequest;
import com.classchat.dto.ConversationMessage;
import com.classchat.dto.ConversationSummary;
import com.classchat.dto.CreateDirectRequest;
import com.classchat.dto.CreateGroupRequest;
import com.classchat.dto.UserResponse;
import com.classchat.model.UserAccount;
import com.classchat.repository.UserAccountRepository;
import com.classchat.service.ConversationService;
import com.classchat.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConversationController {

    private final ConversationService conversationService;
    private final CurrentUserService currentUserService;
    private final UserAccountRepository users;

    public ConversationController(ConversationService conversationService,
                                  CurrentUserService currentUserService,
                                  UserAccountRepository users) {
        this.conversationService = conversationService;
        this.currentUserService = currentUserService;
        this.users = users;
    }

    @GetMapping("/users")
    public List<UserResponse> searchUsers(@RequestParam String handle, Authentication authentication) {
        String query = handle.trim().replaceFirst("^@", "");
        if (query.length() < 2) {
            return List.of();
        }
        UserAccount currentUser = currentUserService.requireByHandle(authentication.getName());
        return users.findTop10ByHandleContainingIgnoreCaseAndIdNotOrderByHandleAsc(query, currentUser.getId())
                .stream()
                .map(user -> new UserResponse(user.getId(), user.getHandle()))
                .toList();
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> list(Authentication authentication) {
        return conversationService.list(currentUserService.requireByHandle(authentication.getName()));
    }

    @PostMapping("/conversations/direct")
    public ConversationSummary openDirect(@Valid @RequestBody CreateDirectRequest request,
                                          Authentication authentication) {
        return conversationService.openDirect(currentUserService.requireByHandle(authentication.getName()),
                request.handle());
    }

    @PostMapping("/conversations/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationSummary createGroup(@Valid @RequestBody CreateGroupRequest request,
                                           Authentication authentication) {
        return conversationService.createGroup(currentUserService.requireByHandle(authentication.getName()), request);
    }

    @PostMapping("/conversations/{id}/members")
    public ConversationSummary addMember(@PathVariable Long id,
                                         @Valid @RequestBody AddGroupMemberRequest request,
                                         Authentication authentication) {
        return conversationService.addGroupMember(currentUserService.requireByHandle(authentication.getName()),
                id, request.handle());
    }

    @DeleteMapping("/conversations/{id}/members/{handle}")
    public ConversationSummary removeMember(@PathVariable Long id,
                                            @PathVariable String handle,
                                            Authentication authentication) {
        return conversationService.removeGroupMember(currentUserService.requireByHandle(authentication.getName()),
                id, handle);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ConversationMessage> history(@PathVariable Long id,
                                             @RequestParam(required = false) Long beforeId,
                                             @RequestParam(defaultValue = "50") int limit,
                                             Authentication authentication) {
        return conversationService.history(currentUserService.requireByHandle(authentication.getName()),
                id, beforeId, limit);
    }

    @PostMapping("/conversations/{id}/read")
    public ConversationSummary markRead(@PathVariable Long id, Authentication authentication) {
        return conversationService.markRead(currentUserService.requireByHandle(authentication.getName()), id);
    }
}
