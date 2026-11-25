package com.classchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnsubscribePushRequest(@NotBlank @Size(max = 2048) String endpoint) {
}
