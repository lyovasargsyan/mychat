package com.classchat.controller;

import com.classchat.config.PushProperties;
import com.classchat.dto.PushConfigResponse;
import com.classchat.dto.PushSubscriptionRequest;
import com.classchat.dto.UnsubscribePushRequest;
import com.classchat.service.CurrentUserService;
import com.classchat.service.ApiException;
import com.classchat.service.PushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushProperties pushProperties;
    private final CurrentUserService currentUserService;
    private final PushSubscriptionService subscriptionService;

    public PushController(PushProperties pushProperties,
                          CurrentUserService currentUserService,
                          PushSubscriptionService subscriptionService) {
        this.pushProperties = pushProperties;
        this.currentUserService = currentUserService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/config")
    public PushConfigResponse config() {
        return new PushConfigResponse(pushProperties.isEnabled(), pushProperties.getApplicationServerKey());
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request,
                                          Authentication authentication) {
        if (!pushProperties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Web Push is not configured on this server.");
        }
        subscriptionService.save(currentUserService.requireByHandle(authentication.getName()), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribePushRequest request,
                                            Authentication authentication) {
        subscriptionService.remove(currentUserService.requireByHandle(authentication.getName()), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
