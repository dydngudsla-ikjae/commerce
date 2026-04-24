package com.commerce.member.presentation;

import com.commerce.member.domain.MemberRole;
import com.commerce.member.domain.MemberStatus;

public record SignupResponse(String email, MemberRole role, MemberStatus status) {
}