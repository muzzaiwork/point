package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 포인트 적립 내역 엔티티
 */
@Entity
@Table(name = "point", indexes = {
        @Index(name = "idx_point_user_id_expiry_date", columnList = "userId, expiryDateTime, isManual, isExpired"),
        @Index(name = "idx_point_accumulation_date", columnList = "regDateTime"),
        @Index(name = "idx_point_expiry_date", columnList = "expiryDateTime, isExpired"),
        @Index(name = "idx_point_order_no", columnList = "orderNo")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Point extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, unique = true)
    private String pointKey; // 고유 키

    private String orderNo; // 적립 근거 주문 번호 (선택 사항)

    @Column(nullable = false)
    private Long accumulatedPoint; // 최초 적립 금액

    @Column(nullable = false)
    private Long remainingPoint; // 사용 가능한 잔액

    @Column(nullable = false)
    private boolean isManual; // 수기 지급 여부

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType type; // 포인트 타입 (FREE, PAID)

    @Column(nullable = false)
    private LocalDateTime expiryDateTime;

    @Column(nullable = false)
    private LocalDate expiryDate;

    private boolean isCancelled; // 적립 취소 여부

    private boolean isExpired; // 만료 여부

    /**
     * 포인트를 사용한다 (잔액 차감)
     * @param useAmount 사용 금액
     */
    public void use(Long useAmount) {
        this.remainingPoint -= useAmount;
    }

    /**
     * 포인트를 복구한다 (잔액 증가)
     * @param restoreAmount 복구 금액
     */
    public void restore(Long restoreAmount) {
        this.remainingPoint += restoreAmount;
    }

    /**
     * 적립을 취소한다.
     * 이미 사용된 금액이 있는 경우 취소할 수 없다.
     */
    public void cancel() {
        if (this.isCancelled) {
            throw new BusinessException(ResultCode.ALREADY_CANCELLED);
        }
        if (!this.accumulatedPoint.equals(this.remainingPoint)) {
            throw new BusinessException(ResultCode.ALREADY_USED);
        }
        this.isCancelled = true;
        this.remainingPoint = 0L;
    }

    /**
     * 만료일을 설정한다.
     * @param expiryDate 새로운 만료일
     */
    public void setExpiryDateTime(LocalDateTime expiryDate) {
        this.expiryDateTime = expiryDate;
    }

    /**
     * 포인트를 만료시킨다.
     */
    public void expire() {
        this.isExpired = true;
        this.remainingPoint = 0L;
    }

    /**
     * 현재 시점 기준으로 만료 여부를 확인한다. (레거시 지원용)
     * @param now 기준 시간
     * @return 만료 여부
     */
    public boolean isExpired(LocalDateTime now) {
        return this.isExpired || now.isAfter(expiryDateTime);
    }
}
