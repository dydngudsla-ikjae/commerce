package com.commerce.member.dto; 

import java.time.LocalDateTime;

public record LoginResponse(String accessToken, String refreshToken, LocalDateTime lastLoginAt) {
}