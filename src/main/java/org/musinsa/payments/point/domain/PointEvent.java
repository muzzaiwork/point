package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 포인트 이벤트 엔티티 (적립/사용/취소/만료/재발급 등 모든 포인트 활동을 기록)
 */
@Entity
@Table(name = "point_detail", indexes = {
        @Index(name = "idx_pd_order_id", columnList = "order_id"),
        @Index(name = "idx_pd_point_accumulation_id", columnList = "point_accumulation_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_accumulation_id")
    private Point point;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointEventType pointEventType;

    @Column(nullable = false)
    private Long amount; // 금액

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_cancel_id")
    private OrderCancel orderCancel; // 어떤 취소 건에 의해 복구되었는지 연결

    public void setOrderCancel(OrderCancel orderCancel) {
        this.orderCancel = orderCancel;
    }
}
