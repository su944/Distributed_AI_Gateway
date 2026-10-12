package com.sunny.gateway.gateway;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatMessage(
        @NotBlank @Pattern(regexp = "system|user|assistant") String role,
        @NotBlank @Size(max = 32768) String content) {}
