package com.commerce.member.application;

import com.commerce.member.domain.Member;
import com.commerce.member.infrastructure.MemberRepository;
import com.commerce.member.presentation.SignupRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(SignupRequest request) {
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member member = Member.create(request.getEmail().toLowerCase(), encodedPassword, request.getName());
        memberRepository.save(member);
    }
}