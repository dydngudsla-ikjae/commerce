package com.commerce.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshResponse(
        @Schema(description = "새 Access Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.abc") String accessToken,
        @Schema(description = "새 Refresh Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIn0.xyz") String refreshToken
) {
}