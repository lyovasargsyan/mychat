package com.classchat.controller;

import com.classchat.dto.ChatSendRequest;
import com.classchat.dto.ConversationMessage;
import com.classchat.dto.ErrorMessage;
import com.classchat.service.ApiException;
import com.classchat.service.ConversationService;
import com.classchat.service.CurrentUserService;
import com.classchat.service.PushNotificationService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ConversationService conversationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CurrentUserService currentUserService;
    private final PushNotificationService pushNotificationService;

    public ChatController(ConversationService conversationService,
                          SimpMessagingTemplate messagingTemplate,
                          CurrentUserService currentUserService,
                          PushNotificationService pushNotificationService) {
        this.conversationService = conversationService;
        this.messagingTemplate = messagingTemplate;
        this.currentUserService = currentUserService;
        this.pushNotificationService = pushNotificationService;
    }

    @MessageMapping("/conversations/{id}/send")
    public void send(@DestinationVariable Long id, @Valid ChatSendRequest request, Principal principal) {
        ConversationMessage message = conversationService.storeMessage(id, principal.getName(), request.content());
        for (String member : conversationService.memberHandles(id)) {
            messagingTemplate.convertAndSendToUser(member, "/queue/conversations/" + id, message);
        }
        try {
            pushNotificationService.notifyConversationMembers(id,
                    currentUserService.requireByHandle(principal.getName()).getId(),
                    principal.getName(), message.content(), message.id());
        } catch (TaskRejectedException exception) {
            log.warn("Push notification queue is full; saved message {} was delivered without push", message.id());
        }
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorMessage handleApiError(ApiException exception) {
        return new ErrorMessage(exception.getMessage());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ErrorMessage handleOther(Exception exception) {
        log.warn("Could not process a chat message", exception);
        return new ErrorMessage("The server could not process your message.");
    }
}
