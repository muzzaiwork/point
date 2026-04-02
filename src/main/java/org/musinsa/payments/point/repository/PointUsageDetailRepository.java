package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.PointUsageDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {
    List<PointUsageDetail> findByOrder(Order order);
}
