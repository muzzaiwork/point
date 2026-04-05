package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.exception.BusinessException;

/**
 * 포인트 사용(주문) 내역 엔티티.
 *
 * <p>포인트 사용 1건을 나타내며, 부분 취소가 여러 번 발생할 수 있다.
 * 취소 금액은 {@code canceledPoint}에 누적되며, {@code orderedPoint}는 불변이다.
 *
 * <p>주문 타입({@code type}) 변화:
 * <pre>
 *   PURCHASE → (부분 취소) → PARTIAL_CANCEL
 *           → (전액 취소) → TOTAL_CANCEL
 * </pre>
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_order_user_id",  columnList = "userId"),
        @Index(name = "idx_order_reg_date_time", columnList = "regDateTime"),
        @Index(name = "idx_order_reg_date",      columnList = "regDate"),
        @Index(name = "idx_order_order_no",      columnList = "orderNo")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID */
    @Column(nullable = false)
    private String userId;

    /** 주문 번호 (외부에서 전달받는 식별자, unique) */
    @Column(nullable = false, unique = true)
    private String orderNo;

    /** 주문 시 사용한 총 포인트 금액 (불변) */
    @Column(nullable = false)
    private Long orderedPoint;

    /** 누적 취소 금액 (부분 취소가 반복될 때마다 증가) */
    @Column(nullable = false)
    private Long canceledPoint;

    /** 주문 타입 (PURCHASE, PARTIAL_CANCEL, TOTAL_CANCEL) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    /** 주문 진행 상태 (IN_PROGRESS, CONFIRMED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // =========================================================================
    // 상태 변경 메서드
    // =========================================================================

    /**
     * 사용 취소 시 취소 금액을 누적하고 주문 타입을 갱신한다.
     *
     * <ul>
     *   <li>취소 가능 잔여 금액({@code orderedPoint - canceledPoint}) 초과 시
     *       {@code CANCEL_AMOUNT_EXCEEDED} 예외 발생</li>
     *   <li>전액 취소 시 {@code TOTAL_CANCEL}, 부분 취소 시 {@code PARTIAL_CANCEL}로 변경</li>
     * </ul>
     *
     * @param cancelAmount 취소 금액
     */
    public void cancel(Long cancelAmount) {
        long cancelableAmount = this.orderedPoint - this.canceledPoint;
        if (cancelableAmount < cancelAmount) {
            throw new BusinessException(ResultCode.CANCEL_AMOUNT_EXCEEDED);
        }

        this.canceledPoint += cancelAmount;

        if (this.orderedPoint.equals(this.canceledPoint)) {
            this.type = OrderType.TOTAL_CANCEL;
        } else {
            this.type = OrderType.PARTIAL_CANCEL;
        }
    }
}
