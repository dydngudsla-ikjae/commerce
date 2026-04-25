package com.commerce.member.dto;

import com.commerce.member.domain.MemberRole;
import com.commerce.member.domain.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(
        @Schema(description = "이메일", example = "user@example.com") String email,
        @Schema(description = "회원 역할", example = "CUSTOMER") MemberRole role,
        @Schema(description = "회원 상태", example = "ACTIVE") MemberStatus status
) {
}