package com.commerce.member.repository;

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
    @Query(value = """
            UPDATE members
            SET login_fail_count = login_fail_count + 1,
                status = CASE WHEN login_fail_count + 1 >= :maxFailCount THEN 'LOCKED' ELSE status END
            WHERE id = :id
            """, nativeQuery = true)
    void incrementFailCountAndLockIfNeeded(@Param("id") Long id, @Param("maxFailCount") int maxFailCount);

    @Modifying
    @Query("UPDATE Member m SET m.status = :status WHERE m.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") MemberStatus status);
}