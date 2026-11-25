package com.classchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSendRequest(@NotBlank @Size(max = 500) String content) {
}
