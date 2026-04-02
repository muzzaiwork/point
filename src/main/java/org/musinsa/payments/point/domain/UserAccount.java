package org.musinsa.payments.point.domain;

import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자 엔티티
 * 개인별 포인트 보유 한도 등을 관리한다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAccount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String name;

    /**
     * 개인별 1회 최대 적립 가능 포인트 제한
     */
    @Column(nullable = false)
    private Long maxAccumulationPoint;

    /**
     * 개인별 최대 보유 가능 포인트 제한
     */
    @Column(nullable = false)
    private Long maxRetentionPoint;

    /**
     * 총 적립 포인트 (유료 + 무료)
     */
    @Builder.Default
    @Column(nullable = false)
    private Long accumulatedPoint = 0L;

    /**
     * 총 유료 적립 포인트
     */
    @Builder.Default
    @Column(nullable = false)
    private Long accumulatedPaidPoint = 0L;

    /**
     * 총 무료 적립 포인트
     */
    @Builder.Default
    @Column(nullable = false)
    private Long accumulatedFreePoint = 0L;

    /**
     * 총 사용 포인트 (유료 + 무료)
     */
    @Builder.Default
    @Column(nullable = false)
    private Long usedPoint = 0L;

    /**
     * 총 유료 사용 포인트
     */
    @Builder.Default
    @Column(nullable = false)
    private Long usedPaidPoint = 0L;

    /**
     * 총 무료 사용 포인트
     */
    @Builder.Default
    @Column(nullable = false)
    private Long usedFreePoint = 0L;

    /**
     * 현재 보유 중인 총 포인트 잔액
     */
    @Builder.Default
    @Column(nullable = false)
    private Long totalPoint = 0L;

    /**
     * 현재 보유 중인 유료 포인트 잔액
     */
    @Builder.Default
    @Column(nullable = false)
    private Long totalPaidPoint = 0L;

    /**
     * 현재 보유 중인 무료 포인트 잔액
     */
    @Builder.Default
    @Column(nullable = false)
    private Long totalFreePoint = 0L;

    /**
     * 포인트 적립
     * @param amount 적립할 금액
     * @param type 포인트 타입 (무료/유료)
     */
    public void addPoint(Long amount, PointType type) {
        if (amount < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "적립 금액은 1포인트 이상이어야 합니다.");
        }
        if (amount > this.maxAccumulationPoint) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "1회 적립 한도(" + this.maxAccumulationPoint + ")를 초과하였습니다.");
        }
        if (this.totalPoint + amount > this.maxRetentionPoint) {
            throw new BusinessException(ResultCode.LIMIT_EXCEEDED, "개인별 최대 보유 가능 포인트(" + this.maxRetentionPoint + ")를 초과할 수 없습니다.");
        }
        
        this.accumulatedPoint += amount;
        this.totalPoint += amount;
        
        if (PointType.PAID.equals(type)) {
            this.accumulatedPaidPoint += amount;
            this.totalPaidPoint += amount;
        } else {
            this.accumulatedFreePoint += amount;
            this.totalFreePoint += amount;
        }
    }

    /**
     * 포인트 사용
     * @param amount 사용될 금액
     * @param type 포인트 타입 (무료/유료)
     */
    public void usePoint(Long amount, PointType type) {
        if (this.totalPoint < amount) {
            throw new BusinessException(ResultCode.POINT_SHORTAGE, "보유 포인트가 부족합니다.");
        }
        
        this.usedPoint += amount;
        this.totalPoint -= amount;
        
        if (PointType.PAID.equals(type)) {
            this.usedPaidPoint += amount;
            this.totalPaidPoint -= amount;
        } else {
            this.usedFreePoint += amount;
            this.totalFreePoint -= amount;
        }
    }

    /**
     * 포인트 적립 취소
     * @param amount 취소할 금액
     * @param type 포인트 타입 (무료/유료)
     */
    public void cancelAccumulation(Long amount, PointType type) {
        this.accumulatedPoint -= amount;
        this.totalPoint -= amount;
        
        if (PointType.PAID.equals(type)) {
            this.accumulatedPaidPoint -= amount;
            this.totalPaidPoint -= amount;
        } else {
            this.accumulatedFreePoint -= amount;
            this.totalFreePoint -= amount;
        }
    }

    /**
     * 포인트 사용 취소 (복구)
     * @param amount 복구할 금액
     * @param type 포인트 타입 (무료/유료)
     */
    public void cancelUsage(Long amount, PointType type) {
        this.usedPoint -= amount;
        this.totalPoint += amount;
        
        if (PointType.PAID.equals(type)) {
            this.usedPaidPoint -= amount;
            this.totalPaidPoint += amount;
        } else {
            this.usedFreePoint -= amount;
            this.totalFreePoint += amount;
        }
    }

    /**
     * 보유 한도 변경
     * @param maxRetentionPoint 새로운 보유 한도
     */
    public void updateMaxRetentionPoint(Long maxRetentionPoint) {
        this.maxRetentionPoint = maxRetentionPoint;
    }
}
