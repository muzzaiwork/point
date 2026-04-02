package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 포인트 사용 상세 내역 엔티티 (어떤 적립 건에서 얼마를 사용했는지 1원 단위로 기록)
 */
@Entity
@Table(name = "point_usage_detail", indexes = {
        @Index(name = "idx_pud_order_id", columnList = "order_id"),
        @Index(name = "idx_pud_point_accumulation_id", columnList = "point_accumulation_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointUsageDetail extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_accumulation_id")
    private Point point;

    @Column(nullable = false)
    private Long amount; // 해당 적립 건에서 사용한 금액

    @Column(nullable = false)
    private Long cancelledAmount; // 해당 상세 내역에서 취소된 금액

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_cancel_id")
    private OrderCancel orderCancel; // 어떤 취소 건에 의해 복구되었는지 연결

    public void setOrderCancel(OrderCancel orderCancel) {
        this.orderCancel = orderCancel;
    }

    @Column(nullable = false)
    private LocalDateTime usageDate;

    @Column(nullable = false)
    private LocalDate usageDay;

    /**
     * 상세 내역의 취소 금액을 추가한다.
     * @param amount 추가할 취소 금액
     */
    public void addCancelledAmount(Long amount) {
        this.cancelledAmount += amount;
    }
}
