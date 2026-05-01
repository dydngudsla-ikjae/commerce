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

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = :status AND o.createdAt < :threshold")
    List<Order> findByStatusAndCreatedAtBefore(@Param("status") OrderStatus status, @Param("threshold") LocalDateTime threshold);

    /**
     * PAYMENT_IN_PROGRESS 상태이고 마지막 retry 또는 updatedAt 기준으로 threshold 이전인 주문 조회
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status = 'PAYMENT_IN_PROGRESS'
              AND (
                (o.lastRetryAt IS NULL AND o.updatedAt < :threshold)
                OR o.lastRetryAt < :threshold
              )
            """)
    List<Order> findRetryablePaymentInProgressOrders(@Param("threshold") LocalDateTime threshold);
}