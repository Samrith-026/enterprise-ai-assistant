package com.example.enterpriseassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentRequest(@NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 200000) String content) {
}
