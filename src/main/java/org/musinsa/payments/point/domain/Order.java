package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private Long totalAmount; // 총 사용 금액 (취소 시 차감됨)

    @Column(nullable = false)
    private Long cancelledAmount; // 취소된 금액

    /**
     * 사용 취소 시 총 사용 금액에서 차감한다.
     * @param cancelAmount 취소할 금액
     */
    public void cancel(Long cancelAmount) {
        if (this.totalAmount < cancelAmount) {
            throw new org.musinsa.payments.point.exception.BusinessException(
                org.musinsa.payments.point.common.ResultCode.BAD_REQUEST, "취소 금액이 사용 금액을 초과할 수 없습니다.");
        }
        this.totalAmount -= cancelAmount;
        this.cancelledAmount += cancelAmount;
    }
}
