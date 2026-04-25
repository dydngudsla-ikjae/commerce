package com.commerce.member.infrastructure;

import com.commerce.member.domain.Member;
import com.commerce.member.domain.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByEmail(String email);
    Optional<Member> findByEmail(String email);

    @Modifying
    @Query("UPDATE Member m SET m.loginFailCount = m.loginFailCount + 1 WHERE m.id = :id")
    void incrementLoginFailCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Member m SET m.status = :status WHERE m.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") MemberStatus status);
}