package com.commerce.member.presentation;

import java.time.LocalDateTime;

public record LoginResponse(String accessToken, String refreshToken, LocalDateTime lastLoginAt) {
}