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

/**
 * 포인트 적립 내역 레포지토리.
 */
public interface PointRepository extends JpaRepository<Point, Long> {

    /**
     * pointKey로 적립 건을 조회한다.
     */
    Optional<Point> findByPointKey(String pointKey);

    /**
     * 특정 사용자의 유효한 포인트 잔액 합계를 조회한다.
     * 만료되거나 취소된 건은 제외한다.
     */
    @Query("SELECT SUM(p.remainingPoint) FROM Point p " +
           "WHERE p.userId = :userId AND p.isExpired = false AND p.isCancelled = false")
    Long getValidTotalRemainingPoint(@Param("userId") String userId);

    /**
     * 사용 가능한 적립 건 목록을 차감 우선순위 순으로 조회한다.
     *
     * <p>우선순위:
     * <ol>
     *   <li>수기 지급(MANUAL) 포인트 우선</li>
     *   <li>만료일 임박 순 (expiryDateTime ASC)</li>
     * </ol>
     * 잔액이 0이거나 만료/취소된 건은 제외한다.
     */
    @Query("SELECT p FROM Point p " +
           "WHERE p.userId = :userId AND p.remainingPoint > 0 AND p.isExpired = false AND p.isCancelled = false " +
           "ORDER BY CASE WHEN p.pointSourceType = 'MANUAL' THEN 1 ELSE 0 END DESC, p.expiryDateTime ASC")
    List<Point> findAvailablePoints(@Param("userId") String userId);

    /**
     * 특정 사용자의 모든 적립 건을 최신 순으로 조회한다 (Admin 조회용).
     */
    List<Point> findByUserIdOrderByIdDesc(String userId);

    /**
     * originPointKey로 연결된 재발급 포인트 목록을 조회한다.
     * 만료 후 취소로 생성된 AUTO_RESTORED 포인트 추적에 사용된다.
     */
    List<Point> findByOriginPointKey(String originPointKey);

    /**
     * rootPointKey로 연결된 전체 계보 포인트 목록을 조회한다.
     */
    List<Point> findByRootPointKey(String rootPointKey);

    /**
     * 조건 기반 포인트 검색 (Admin 관리 화면용).
     * 모든 조건은 선택적이며, null이면 해당 조건을 무시한다.
     */
    @Query("SELECT p FROM Point p WHERE " +
           "(:userId IS NULL OR p.userId = :userId) AND " +
           "(:cancelled IS NULL OR p.isCancelled = :cancelled) AND " +
           "(:type IS NULL OR p.type = :type) AND " +
           "(:sourceType IS NULL OR p.pointSourceType = :sourceType) AND " +
           "(:startDate IS NULL OR p.regDate >= :startDate) AND " +
           "(:endDate IS NULL OR p.regDate <= :endDate) AND " +
           "(:pointKey IS NULL OR p.pointKey = :pointKey) AND " +
           "(:#{#pointKeySet == null} = true OR p.pointKey IN :pointKeySet) " +
           "ORDER BY p.id DESC")
    Page<Point> searchPoints(
            @Param("userId")      String userId,
            @Param("cancelled")   Boolean cancelled,
            @Param("type")        PointType type,
            @Param("sourceType")  PointSourceType sourceType,
            @Param("startDate")   LocalDate startDate,
            @Param("endDate")     LocalDate endDate,
            @Param("pointKey")    String pointKey,
            @Param("pointKeySet") java.util.Set<String> pointKeySet,
            Pageable pageable
    );
}
