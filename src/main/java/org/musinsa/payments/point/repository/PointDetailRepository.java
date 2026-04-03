package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.Point;
import org.musinsa.payments.point.domain.PointDetail;
import org.musinsa.payments.point.domain.PointDetailType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointDetailRepository extends JpaRepository<PointDetail, Long> {
    List<PointDetail> findByOrder(Order order);

    /**
     * 특정 주문의 상세 사용 내역을 ID 역순(최신 사용순)으로 조회한다.
     * 사용 취소 시 나중에 사용된 것부터 먼저 복구하기 위함.
     */
    List<PointDetail> findByOrderAndDetailTypeOrderByIdDesc(Order order, PointDetailType detailType);

    List<PointDetail> findByOrderAndPointAndDetailType(Order order, Point point, PointDetailType detailType);
}
