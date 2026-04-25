package com.commerce.member.application;

import com.commerce.member.domain.Member;
import com.commerce.member.infrastructure.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LastLoginUpdateService {

    private final MemberRepository memberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLastLoginAt(Long memberId) {
        memberRepository.findById(memberId).ifPresent(member -> {
            member.updateLastLoginAt(LocalDateTime.now());
        });
    }
}