package com.classchat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank @Size(max = 2048) String endpoint,
        @NotNull @Valid Keys keys) {

    public record Keys(
            @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]+={0,2}") String p256dh,
            @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9_-]+={0,2}") String auth) {
    }
}
