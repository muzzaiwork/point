package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 포인트 사용(주문) 내역 레포지토리.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * orderNo로 주문을 조회한다.
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 조건 기반 주문 검색 (Admin 관리 화면용).
     * 모든 조건은 선택적이며, null이면 해당 조건을 무시한다.
     */
    @Query("""
        SELECT o FROM Order o
        WHERE (:userId IS NULL OR o.userId = :userId)
          AND (:orderNo IS NULL OR o.orderNo LIKE %:orderNo%)
          AND (:type IS NULL OR o.type = :type)
          AND (:startDate IS NULL OR o.regDateTime >= :startDate)
          AND (:endDate IS NULL OR o.regDateTime <= :endDate)
        ORDER BY o.regDateTime DESC
    """)
    Page<Order> searchOrders(
            @Param("userId")    String userId,
            @Param("orderNo")   String orderNo,
            @Param("type")      OrderType type,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate,
            Pageable pageable
    );
}
