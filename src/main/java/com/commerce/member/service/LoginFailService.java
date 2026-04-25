package com.commerce.member.service;

import com.commerce.global.exception.BusinessException;
import com.commerce.global.exception.ErrorCode;
import com.commerce.member.domain.Member;
import com.commerce.member.domain.MemberStatus;
import com.commerce.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginFailService {

    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    private final MemberRepository memberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFail(Long memberId) {
        memberRepository.incrementLoginFailCount(memberId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID));
        if (member.getLoginFailCount() >= MAX_LOGIN_FAIL_COUNT) {
            memberRepository.updateStatus(memberId, MemberStatus.LOCKED);
        }
    }
}