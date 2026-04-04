package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointRepository extends JpaRepository<Point, Long> {
    Optional<Point> findByPointKey(String pointKey);

    @Query("SELECT SUM(p.remainingPoint) FROM Point p WHERE p.userId = :userId AND p.isExpired = false AND p.isCancelled = false")
    Long getValidTotalRemainingPoint(@Param("userId") String userId);

    // 수기 지급 우선, 만료일 임박 순
    @Query("SELECT p FROM Point p WHERE p.userId = :userId AND p.remainingPoint > 0 AND p.isExpired = false AND p.isCancelled = false " +
           "ORDER BY CASE WHEN p.pointSourceType = 'MANUAL' THEN 1 ELSE 0 END DESC, p.expiryDateTime ASC")
    List<Point> findAvailablePoints(@Param("userId") String userId);

    List<Point> findByUserIdOrderByIdDesc(String userId);
}
