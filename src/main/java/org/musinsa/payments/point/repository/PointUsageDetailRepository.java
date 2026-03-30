package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.PointUsage;
import org.musinsa.payments.point.domain.PointUsageDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {
    List<PointUsageDetail> findByPointUsage(PointUsage pointUsage);
}
