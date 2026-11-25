package com.classchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddGroupMemberRequest(@NotBlank @Size(max = 31) String handle) {
}
