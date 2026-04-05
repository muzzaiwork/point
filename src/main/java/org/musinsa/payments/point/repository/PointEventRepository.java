package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.OrderCancel;
import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.PointEvent;
import org.musinsa.payments.point.domain.PointEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 포인트 이벤트 이력 레포지토리.
 */
public interface PointEventRepository extends JpaRepository<PointEvent, Long> {

    /**
     * 특정 주문에 연결된 모든 이벤트를 조회한다.
     */
    List<PointEvent> findByOrder(Order order);

    /**
     * 특정 주문의 이벤트를 타입 필터링 후 ID 역순(LIFO)으로 조회한다.
     * 사용 취소 시 가장 최근에 차감된 적립 건부터 복구하기 위해 사용한다.
     */
    List<PointEvent> findByOrderAndPointEventTypeOrderByIdDesc(Order order, PointEventType pointEventType);

    /**
     * 특정 주문 + 특정 적립 건 + 이벤트 타입으로 이벤트 목록을 조회한다.
     * 유효한 포인트의 이미 취소된 금액 계산에 사용한다 (USE_CANCEL 합산).
     */
    List<PointEvent> findByOrderAndPointAndPointEventType(Order order, Point point, PointEventType pointEventType);

    /**
     * 특정 주문에 연결된 이벤트 중, 이벤트 타입과 originPointKey가 일치하는 것을 조회한다.
     * 만료된 포인트의 이미 취소된 금액 계산에 사용한다 (EXPIRED_CANCEL_RESTORE 합산).
     */
    @Query("SELECT pe FROM PointEvent pe " +
           "WHERE pe.order = :order " +
           "AND pe.pointEventType = :eventType " +
           "AND pe.point.originPointKey = :originPointKey")
    List<PointEvent> findByOrderAndPointEventTypeAndOriginPointKey(
            @Param("order")          Order order,
            @Param("eventType")      PointEventType eventType,
            @Param("originPointKey") String originPointKey
    );

    /**
     * 특정 취소 이력(OrderCancel)과 이벤트 타입으로 이벤트 목록을 조회한다.
     */
    List<PointEvent> findByOrderCancelAndPointEventType(OrderCancel orderCancel, PointEventType pointEventType);

    /**
     * 특정 pointKey에 연결된 모든 이벤트 이력을 등록 순으로 조회한다.
     */
    @Query("SELECT pe FROM PointEvent pe WHERE pe.point.pointKey = :pointKey ORDER BY pe.id ASC")
    List<PointEvent> findAllByPointKey(@Param("pointKey") String pointKey);

    /**
     * 특정 사용자의 모든 포인트 이벤트 이력을 등록 순으로 조회한다.
     * Point 또는 Order 중 하나를 통해 userId를 매칭한다.
     */
    @Query("SELECT pe FROM PointEvent pe " +
           "LEFT JOIN pe.point p LEFT JOIN pe.order o " +
           "WHERE p.userId = :userId OR o.userId = :userId " +
           "ORDER BY pe.id ASC")
    List<PointEvent> findAllByUserId(@Param("userId") String userId);

    /**
     * 일별 집계: 특정 날짜 범위의 날짜별·이벤트 타입별 금액 합계를 조회한다 (정산용).
     */
    @Query("SELECT pe.regDate, pe.pointEventType, SUM(pe.amount) FROM PointEvent pe " +
           "WHERE pe.regDate BETWEEN :startDate AND :endDate " +
           "GROUP BY pe.regDate, pe.pointEventType " +
           "ORDER BY pe.regDate DESC, pe.pointEventType ASC")
    List<Object[]> findDailyAggregation(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    /**
     * 월별 집계: 특정 날짜 범위의 연월별·이벤트 타입별 금액 합계를 조회한다 (정산용).
     */
    @Query("SELECT FUNCTION('FORMATDATETIME', pe.regDate, 'yyyy-MM'), pe.pointEventType, SUM(pe.amount) " +
           "FROM PointEvent pe " +
           "WHERE pe.regDate BETWEEN :startDate AND :endDate " +
           "GROUP BY FUNCTION('FORMATDATETIME', pe.regDate, 'yyyy-MM'), pe.pointEventType " +
           "ORDER BY FUNCTION('FORMATDATETIME', pe.regDate, 'yyyy-MM') DESC, pe.pointEventType ASC")
    List<Object[]> findMonthlyAggregation(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );

    /**
     * 연도별 집계: 특정 날짜 범위의 연도별·이벤트 타입별 금액 합계를 조회한다 (정산용).
     */
    @Query("SELECT FUNCTION('YEAR', pe.regDate), pe.pointEventType, SUM(pe.amount) " +
           "FROM PointEvent pe " +
           "WHERE pe.regDate BETWEEN :startDate AND :endDate " +
           "GROUP BY FUNCTION('YEAR', pe.regDate), pe.pointEventType " +
           "ORDER BY FUNCTION('YEAR', pe.regDate) DESC, pe.pointEventType ASC")
    List<Object[]> findYearlyAggregation(
            @Param("startDate") LocalDate startDate,
            @Param("endDate")   LocalDate endDate
    );
}
