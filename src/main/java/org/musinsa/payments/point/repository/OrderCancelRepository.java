package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.OrderCancel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderCancelRepository extends JpaRepository<OrderCancel, Long> {
    List<OrderCancel> findByOrder(Order order);
}
