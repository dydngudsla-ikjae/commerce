package com.commerce.member.application;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.global.jwt.JwtProvider;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.RefreshToken;
import com.commerce.member.infrastructure.MemberRepository;
import com.commerce.member.infrastructure.RefreshTokenRepository;
import com.commerce.member.presentation.LoginRequest;
import com.commerce.member.presentation.LoginResponse;
import com.commerce.member.presentation.SignupRequest;
import com.commerce.member.presentation.SignupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.getEmail().toLowerCase();
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member member = Member.create(email, encodedPassword, request.getName());
        memberRepository.save(member);
        return new SignupResponse(member.getEmail(), member.getRole(), member.getStatus());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID));

        if (!member.isLoginable()) {
            throw new BusinessException(ErrorCode.AUTH_INVALID);
        }

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            member.onLoginFail();
            throw new BusinessException(ErrorCode.AUTH_INVALID);
        }

        member.onLoginSuccess();

        String accessToken = jwtProvider.generateAccessToken(member.getId(), member.getRole().name());
        String refreshTokenStr = jwtProvider.generateRefreshToken(member.getId());

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProvider.getRefreshExpirationMs() / 1000);

        refreshTokenRepository.deleteByMemberId(member.getId());
        refreshTokenRepository.save(RefreshToken.create(member.getId(), refreshTokenStr, expiresAt));

        return new LoginResponse(accessToken, refreshTokenStr, member.getLastLoginAt());
    }
}