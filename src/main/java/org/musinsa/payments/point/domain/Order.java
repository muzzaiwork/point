package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 포인트 사용(주문) 내역 엔티티
 */
@Entity
@Table(name = "`order`", indexes = {
        @Index(name = "idx_order_user_id_usage_date", columnList = "userId, regDateTime"),
        @Index(name = "idx_order_usage_date", columnList = "regDateTime"),
        @Index(name = "idx_order_order_no", columnList = "orderNo")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, unique = true)
    private String orderNo; // 주문 번호 (식별자로 사용)

    @Column(nullable = false)
    private Long orderedPoint; // 주문 시 사용한 총 포인트 금액 (불변)

    @Column(nullable = false)
    private Long canceledPoint; // 취소된 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type; // 취소 상태 (PURCHASE, PARTIAL_CANCEL, TOTAL_CANCEL)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status; // 진행 상태 (IN_PROGRESS, CONFIRMED)

    /**
     * 사용 취소 시 취소된 금액을 누적한다.
     * @param cancelAmount 취소할 금액
     */
    public void cancel(Long cancelAmount) {
        if (this.orderedPoint - this.canceledPoint < cancelAmount) {
            throw new org.musinsa.payments.point.exception.BusinessException(
                org.musinsa.payments.point.common.ResultCode.CANCEL_AMOUNT_EXCEEDED);
        }
        this.canceledPoint += cancelAmount;
        
        if (this.orderedPoint.equals(this.canceledPoint)) {
            this.type = OrderType.TOTAL_CANCEL;
        } else {
            this.type = OrderType.PARTIAL_CANCEL;
        }
    }
}
