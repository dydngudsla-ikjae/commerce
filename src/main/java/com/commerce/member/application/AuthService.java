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
import com.commerce.member.presentation.RefreshRequest;
import com.commerce.member.presentation.RefreshResponse;
import com.commerce.member.presentation.SignupRequest;
import com.commerce.member.presentation.SignupResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
    private final LoginFailService loginFailService;
    private final LastLoginUpdateService lastLoginUpdateService;

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
            loginFailService.recordLoginFail(member.getId());
            throw new BusinessException(ErrorCode.AUTH_INVALID);
        }

        member.onLoginSuccess();

        String accessToken = jwtProvider.generateAccessToken(member.getId(), member.getRole().name());
        String refreshTokenStr = jwtProvider.generateRefreshToken(member.getId());

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProvider.getRefreshExpirationMs() / 1000);

        refreshTokenRepository.deleteByMemberId(member.getId());
        refreshTokenRepository.save(RefreshToken.create(member.getId(), refreshTokenStr, expiresAt));

        LocalDateTime loginTime = LocalDateTime.now();
        LocalDateTime lastLoginAt = null;
        try {
            lastLoginUpdateService.updateLastLoginAt(member.getId(), loginTime);
            lastLoginAt = loginTime;
        } catch (Exception e) {
            // last_login_at 업데이트 실패해도 로그인은 성공 처리
        }

        return new LoginResponse(accessToken, refreshTokenStr, lastLoginAt);
    }

    @Transactional
    public void logout(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        String tokenValue = request.getRefreshToken();

        Claims claims;
        try {
            claims = jwtProvider.parse(tokenValue);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        Long memberId;
        try {
            memberId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        RefreshToken storedToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        refreshTokenRepository.delete(storedToken);

        String newAccessToken = jwtProvider.generateAccessToken(member.getId(), member.getRole().name());
        String newRefreshTokenStr = jwtProvider.generateRefreshToken(member.getId());

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProvider.getRefreshExpirationMs() / 1000);
        refreshTokenRepository.save(RefreshToken.create(member.getId(), newRefreshTokenStr, expiresAt));

        return new RefreshResponse(newAccessToken, newRefreshTokenStr);
    }
}