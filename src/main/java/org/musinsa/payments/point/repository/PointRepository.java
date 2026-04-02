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

    @Query("SELECT SUM(p.remainingAmount) FROM Point p WHERE p.userId = :userId AND p.expiryDateTime > :now AND p.isCancelled = false")
    Long getValidTotalRemainingAmount(@Param("userId") String userId, @Param("now") LocalDateTime now);

    // 수기 지급 우선, 만료일 임박 순
    @Query("SELECT p FROM Point p WHERE p.userId = :userId AND p.remainingAmount > 0 AND p.expiryDateTime > :now AND p.isCancelled = false " +
           "ORDER BY p.isManual DESC, p.expiryDateTime ASC")
    List<Point> findAvailablePoints(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
