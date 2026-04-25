package com.commerce.member.presentation;

import com.commerce.global.jwt.AuthMember;
import com.commerce.member.application.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public MemberInfo me(@AuthenticationPrincipal AuthMember authMember) {
        return new MemberInfo(authMember.id(), authMember.role());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthMember authMember) {
        memberService.withdraw(authMember.id());
    }

    public record MemberInfo(Long id, String role) {
    }
}