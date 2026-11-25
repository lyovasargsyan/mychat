package com.classchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGroupRequest(
        @NotBlank @Size(max = 80) String title,
        @Size(max = 49) List<@NotBlank @Size(max = 31) String> handles) {
}
