package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.OrderCancel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 포인트 사용 취소 이력 레포지토리.
 */
public interface OrderCancelRepository extends JpaRepository<OrderCancel, Long> {

    /**
     * 특정 주문에 연결된 모든 취소 이력을 조회한다.
     */
    List<OrderCancel> findByOrder(Order order);
}
