package com.commerce.member.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.global.jwt.JwtProvider;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.RefreshToken;
import com.commerce.member.repository.MemberRepository;
import com.commerce.member.repository.RefreshTokenRepository;
import com.commerce.member.dto.LoginRequest;
import com.commerce.member.dto.LoginResponse;
import com.commerce.member.dto.RefreshRequest;
import com.commerce.member.dto.RefreshResponse;
import com.commerce.member.dto.SignupRequest;
import com.commerce.member.dto.SignupResponse;
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

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 이메일 대소문자 정규화: DB 유니크 제약만으로는 대소문자 중복을 막지 못함
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

        LocalDateTime loginTime = LocalDateTime.now();
        member.onLoginSuccess(loginTime);

        String accessToken = jwtProvider.generateAccessToken(member.getId(), member.getRole().name());
        String refreshTokenStr = jwtProvider.generateRefreshToken(member.getId());

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProvider.getRefreshExpirationMs() / 1000);

        // 리프레시 토큰 교체(rotation): 기존 토큰 삭제 후 신규 발급 → 탈취된 이전 토큰 무효화
        refreshTokenRepository.deleteByMemberId(member.getId());
        refreshTokenRepository.save(RefreshToken.create(member.getId(), refreshTokenStr, expiresAt));

        return new LoginResponse(accessToken, refreshTokenStr, loginTime);
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

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));

        // 사용된 리프레시 토큰 즉시 삭제 후 새 토큰 발급 (1회용 rotation)
        refreshTokenRepository.delete(storedToken);

        String newAccessToken = jwtProvider.generateAccessToken(member.getId(), member.getRole().name());
        String newRefreshTokenStr = jwtProvider.generateRefreshToken(member.getId());

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtProvider.getRefreshExpirationMs() / 1000);
        refreshTokenRepository.save(RefreshToken.create(member.getId(), newRefreshTokenStr, expiresAt));

        return new RefreshResponse(newAccessToken, newRefreshTokenStr);
    }
}