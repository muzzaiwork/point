package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.PointUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointUsageRepository extends JpaRepository<PointUsage, Long> {
    Optional<PointUsage> findByPointKey(String pointKey);
}
