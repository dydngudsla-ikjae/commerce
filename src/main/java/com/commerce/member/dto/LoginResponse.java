package com.commerce.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record LoginResponse(
        @Schema(description = "Access Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.abc") String accessToken,
        @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.xyz") String refreshToken,
        @Schema(description = "마지막 로그인 시각", example = "2024-01-15T09:00:00") LocalDateTime lastLoginAt
) {
}