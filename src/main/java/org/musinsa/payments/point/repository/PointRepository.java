package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.PointSourceType;
import org.musinsa.payments.point.domain.PointType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    Optional<Point> findByOriginPointKey(String originPointKey);

    @Query("SELECT p FROM Point p WHERE " +
           "(:userId IS NULL OR p.userId = :userId) AND " +
           "(:cancelled IS NULL OR p.isCancelled = :cancelled) AND " +
           "(:type IS NULL OR p.type = :type) AND " +
           "(:sourceType IS NULL OR p.pointSourceType = :sourceType) AND " +
           "(:startDate IS NULL OR p.expiryDate >= :startDate) AND " +
           "(:endDate IS NULL OR p.expiryDate <= :endDate) " +
           "ORDER BY p.id DESC")
    Page<Point> searchPoints(
            @Param("userId") String userId,
            @Param("cancelled") Boolean cancelled,
            @Param("type") PointType type,
            @Param("sourceType") PointSourceType sourceType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable
    );
}
