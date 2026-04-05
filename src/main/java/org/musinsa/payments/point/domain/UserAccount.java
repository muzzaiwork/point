package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.exception.BusinessException;

/**
 * 사용자 포인트 계정 엔티티.
 *
 * <p>사용자별 포인트 잔액(무료/유료 구분)과 적립·사용 한도를 관리한다.
 * 모든 잔액 변경은 이 엔티티의 메서드를 통해서만 이루어지며,
 * 비즈니스 규칙(한도 초과 등)도 내부에서 검증한다.
 *
 * <p>동시성 제어:
 * 서비스 레이어에서 Pessimistic Lock을 걸고 조회하므로,
 * 동시 요청에 의한 잔액 불일치가 발생하지 않는다.
 */
@Entity
@Table(name = "user_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 식별 ID (unique) */
    @Column(nullable = false, unique = true)
    private String userId;

    /** 사용자 이름 */
    @Column(nullable = false)
    private String name;

    /** 1회 최대 적립 가능 포인트 한도 */
    @Column(nullable = false)
    private Long maxAccumulationPoint;

    /** 개인별 최대 보유 가능 포인트 한도 */
    @Column(nullable = false)
    private Long maxRetentionPoint;

    // -------------------------------------------------------------------------
    // 적립 누계
    // -------------------------------------------------------------------------

    /** 총 적립 포인트 (무료 + 유료) */
    @Builder.Default
    @Column(nullable = false)
    private Long accumulatedPoint = 0L;

    /** 총 유료 적립 포인트 */
    @Builder.Default
    @Column(nullable = false)
    private Long accumulatedPaidPoint = 0L;

    /** 총 무료 적립 포인트 */
    @Builder.Default
    @Column(nullable = false)
    private Long accumulatedFreePoint = 0L;

    // -------------------------------------------------------------------------
    // 사용 누계
    // -------------------------------------------------------------------------

    /** 총 사용 포인트 (무료 + 유료) */
    @Builder.Default
    @Column(nullable = false)
    private Long usedPoint = 0L;

    /** 총 유료 사용 포인트 */
    @Builder.Default
    @Column(nullable = false)
    private Long usedPaidPoint = 0L;

    /** 총 무료 사용 포인트 */
    @Builder.Default
    @Column(nullable = false)
    private Long usedFreePoint = 0L;

    // -------------------------------------------------------------------------
    // 잔액
    // -------------------------------------------------------------------------

    /** 현재 보유 중인 총 포인트 잔액 (무료 + 유료) */
    @Builder.Default
    @Column(nullable = false)
    private Long remainingPoint = 0L;

    /** 현재 보유 중인 유료 포인트 잔액 */
    @Builder.Default
    @Column(nullable = false)
    private Long remainingPaidPoint = 0L;

    /** 현재 보유 중인 무료 포인트 잔액 */
    @Builder.Default
    @Column(nullable = false)
    private Long remainingFreePoint = 0L;

    // =========================================================================
    // 상태 변경 메서드
    // =========================================================================

    /**
     * 포인트를 적립한다.
     *
     * <p>검증 조건:
     * <ul>
     *   <li>적립 금액 1P 미만 → {@code INVALID_ACCUMULATION_AMOUNT}</li>
     *   <li>1회 적립 한도 초과 → {@code ACCUMULATION_LIMIT_EXCEEDED}</li>
     *   <li>최대 보유 한도 초과 → {@code RETENTION_LIMIT_EXCEEDED}</li>
     * </ul>
     *
     * @param amount 적립 금액
     * @param type   포인트 타입 (FREE, PAID)
     */
    public void accumulatePoint(Long amount, PointType type) {
        if (amount < 1) {
            throw new BusinessException(ResultCode.INVALID_ACCUMULATION_AMOUNT);
        }
        if (amount > this.maxAccumulationPoint) {
            throw new BusinessException(ResultCode.ACCUMULATION_LIMIT_EXCEEDED,
                    "1회 적립 가능 한도(" + this.maxAccumulationPoint + ")를 초과하였습니다.");
        }
        if (this.remainingPoint + amount > this.maxRetentionPoint) {
            throw new BusinessException(ResultCode.RETENTION_LIMIT_EXCEEDED,
                    "개인별 최대 보유 가능 포인트(" + this.maxRetentionPoint + ")를 초과할 수 없습니다.");
        }

        this.accumulatedPoint += amount;
        this.remainingPoint   += amount;

        if (PointType.PAID.equals(type)) {
            this.accumulatedPaidPoint += amount;
            this.remainingPaidPoint   += amount;
        } else {
            this.accumulatedFreePoint += amount;
            this.remainingFreePoint   += amount;
        }
    }

    /**
     * 포인트를 사용한다.
     *
     * <p>잔액 부족 시 {@code POINT_SHORTAGE} 예외를 발생시킨다.
     *
     * @param amount 사용 금액
     * @param type   포인트 타입 (FREE, PAID)
     */
    public void usePoint(Long amount, PointType type) {
        if (this.remainingPoint < amount) {
            throw new BusinessException(ResultCode.POINT_SHORTAGE, "보유 포인트가 부족합니다.");
        }

        this.usedPoint      += amount;
        this.remainingPoint -= amount;

        if (PointType.PAID.equals(type)) {
            this.usedPaidPoint      += amount;
            this.remainingPaidPoint -= amount;
        } else {
            this.usedFreePoint      += amount;
            this.remainingFreePoint -= amount;
        }
    }

    /**
     * 포인트 적립을 취소한다 (적립 철회).
     * 적립 누계와 잔액을 함께 차감한다.
     *
     * @param amount 취소 금액
     * @param type   포인트 타입 (FREE, PAID)
     */
    public void cancelAccumulation(Long amount, PointType type) {
        this.accumulatedPoint -= amount;
        this.remainingPoint   -= amount;

        if (PointType.PAID.equals(type)) {
            this.accumulatedPaidPoint -= amount;
            this.remainingPaidPoint   -= amount;
        } else {
            this.accumulatedFreePoint -= amount;
            this.remainingFreePoint   -= amount;
        }
    }

    /**
     * 포인트 사용을 취소한다 (잔액 복구).
     * 사용 누계를 차감하고 잔액을 증가시킨다.
     *
     * @param amount 복구 금액
     * @param type   포인트 타입 (FREE, PAID)
     */
    public void cancelUsage(Long amount, PointType type) {
        this.usedPoint      -= amount;
        this.remainingPoint += amount;

        if (PointType.PAID.equals(type)) {
            this.usedPaidPoint      -= amount;
            this.remainingPaidPoint += amount;
        } else {
            this.usedFreePoint      -= amount;
            this.remainingFreePoint += amount;
        }
    }

    /**
     * 개인별 최대 보유 한도를 변경한다.
     *
     * @param maxRetentionPoint 새로운 보유 한도
     */
    public void updateMaxRetentionPoint(Long maxRetentionPoint) {
        this.maxRetentionPoint = maxRetentionPoint;
    }
}
