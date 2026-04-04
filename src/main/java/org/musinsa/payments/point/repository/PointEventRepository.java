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

public interface PointEventRepository extends JpaRepository<PointEvent, Long> {
    List<PointEvent> findByOrder(Order order);

    /**
     * 특정 주문의 상세 사용 내역을 ID 역순(최신 사용순)으로 조회한다.
     * 사용 취소 시 나중에 사용된 것부터 먼저 복구하기 위함.
     */
    List<PointEvent> findByOrderAndPointEventTypeOrderByIdDesc(Order order, PointEventType pointEventType);

    List<PointEvent> findByOrderAndPointAndPointEventType(Order order, Point point, PointEventType pointEventType);

    /**
     * 특정 주문에 연결된 EXPIRED_CANCEL_RESTORE 이벤트 중 originPointKey가 일치하는 것을 조회한다.
     * 만료된 포인트에 대한 이미 취소된 금액 계산에 사용된다.
     */
    @Query("SELECT pe FROM PointEvent pe WHERE pe.order = :order AND pe.pointEventType = :eventType AND pe.point.originPointKey = :originPointKey")
    List<PointEvent> findByOrderAndPointEventTypeAndOriginPointKey(@Param("order") Order order, @Param("eventType") PointEventType eventType, @Param("originPointKey") String originPointKey);

    /**
     * 특정 포인트 건(pointKey)에 연결된 모든 이벤트 이력을 조회한다.
     */
    List<PointEvent> findByOrderCancelAndPointEventType(OrderCancel orderCancel, PointEventType pointEventType);

    @Query("SELECT pe FROM PointEvent pe WHERE pe.point.pointKey = :pointKey ORDER BY pe.id ASC")
    List<PointEvent> findAllByPointKey(@Param("pointKey") String pointKey);

    /**
     * 특정 사용자의 모든 포인트 이벤트 이력을 조회한다.
     * Point 또는 Order 중 하나를 통해 userId를 매칭한다.
     */
    @Query("SELECT pe FROM PointEvent pe LEFT JOIN pe.point p LEFT JOIN pe.order o WHERE p.userId = :userId OR o.userId = :userId ORDER BY pe.id ASC")
    List<PointEvent> findAllByUserId(@Param("userId") String userId);

    /**
     * 일별 집계 (정산용): 특정 날짜 범위의 이벤트 타입별 합계를 조회한다.
     */
    @Query("SELECT pe.regDate, pe.pointEventType, SUM(pe.amount) FROM PointEvent pe WHERE pe.regDate BETWEEN :startDate AND :endDate GROUP BY pe.regDate, pe.pointEventType ORDER BY pe.regDate ASC, pe.pointEventType ASC")
    List<Object[]> findDailyAggregation(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
