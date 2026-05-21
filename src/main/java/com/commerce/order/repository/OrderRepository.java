package com.commerce.order.repository;

import com.commerce.order.domain.Order;
import com.commerce.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByMemberId(Long memberId, Pageable pageable);

    // fetch join으로 items 한 번에 조회 (N+1 방지)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    // 만료 스케줄러용: items도 fetch해야 재고 release 시 N+1이 발생하지 않음
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = :status AND o.createdAt < :threshold")
    List<Order> findByStatusAndCreatedAtBefore(@Param("status") OrderStatus status, @Param("threshold") LocalDateTime threshold);

    // pgTransactionId IS NOT NULL: charge()는 성공했지만 confirmPayment()가 실패한 주문만 재시도
    // lastRetryAt IS NULL → 첫 재시도: updatedAt 기준(startPayment 시각)으로 interval 판단
    @Query("""
            SELECT o FROM Order o
            WHERE o.status = :status
              AND o.pgTransactionId IS NOT NULL
              AND (
                (o.lastRetryAt IS NULL AND o.updatedAt < :threshold)
                OR o.lastRetryAt < :threshold
              )
            """)
    List<Order> findRetryablePaymentInProgressOrders(
            @Param("status") OrderStatus status,
            @Param("threshold") LocalDateTime threshold);
}