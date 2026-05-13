package com.commerce.member.service;

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

    // REQUIRES_NEW: 외부 로그인 트랜잭션이 롤백되더라도 실패 횟수는 반드시 DB에 반영되어야 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFail(Long memberId) {
        memberRepository.incrementFailCountAndLockIfNeeded(memberId, MAX_LOGIN_FAIL_COUNT);
    }
}