package com.commerce.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(nullable = false)
    private int loginFailCount;

    private LocalDateTime lastLoginAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Member create(String email, String encodedPassword, String name) {
        Member member = new Member();
        member.email = email;
        member.password = encodedPassword;
        member.name = name;
        member.role = MemberRole.CUSTOMER;
        member.status = MemberStatus.ACTIVE;
        member.loginFailCount = 0;
        return member;
    }

    public boolean isLoginable() {
        return this.status == MemberStatus.ACTIVE;
    }

    // @Modifying 쿼리 대신 엔티티 업데이트를 사용하는 이유:
    // login() 트랜잭션 안에서 @Modifying을 쓰면 member 행에 UPDATE 락이 걸린 채로
    // REQUIRES_NEW(lastLoginUpdateService)가 같은 행을 UPDATE하려다 데드락이 발생한다.
    public void onLoginSuccess() {
        this.loginFailCount = 0;
    }

    public void updateLastLoginAt(LocalDateTime loginAt) {
        this.lastLoginAt = loginAt;
    }

    public void withdraw(String deletedEmail) {
        this.status = MemberStatus.DELETED;
        this.email = deletedEmail;
        this.name = "탈퇴 사용자";
    }
}