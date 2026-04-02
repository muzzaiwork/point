package org.musinsa.payments.point.repository;

import org.musinsa.payments.point.domain.Order;
import org.musinsa.payments.point.domain.PointUsageDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointUsageDetailRepository extends JpaRepository<PointUsageDetail, Long> {
    List<PointUsageDetail> findByOrder(Order order);

    /**
     * 특정 주문의 상세 사용 내역을 ID 역순(최신 사용순)으로 조회한다.
     * 사용 취소 시 나중에 사용된 것부터 먼저 복구하기 위함.
     */
    List<PointUsageDetail> findByOrderOrderByIdDesc(Order order);
}
