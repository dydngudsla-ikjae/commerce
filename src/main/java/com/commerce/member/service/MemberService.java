package com.commerce.member.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.MemberStatus;
import com.commerce.member.repository.MemberRepository;
import com.commerce.member.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (member.getStatus() == MemberStatus.DELETED) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }

        String deletedEmail = member.getEmail() + "_deleted_" + Instant.now().toEpochMilli();
        member.withdraw(deletedEmail);

        refreshTokenRepository.deleteByMemberId(memberId);
    }
}