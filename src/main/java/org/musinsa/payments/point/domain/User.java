package org.musinsa.payments.point.domain;

import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class User extends BaseEntity {
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
     * 현재 보유 중인 총 포인트 잔액
     */
    @Column(nullable = false)
    private Long totalPoint;

    /**
     * 포인트 적립
     * @param amount 적립할 금액
     */
    public void addPoint(Long amount) {
        if (amount < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "적립 금액은 1포인트 이상이어야 합니다.");
        }
        if (amount > this.maxAccumulationPoint) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "1회 적립 한도(" + this.maxAccumulationPoint + ")를 초과하였습니다.");
        }
        if (this.totalPoint + amount > this.maxRetentionPoint) {
            throw new BusinessException(ResultCode.LIMIT_EXCEEDED, "개인별 최대 보유 가능 포인트(" + this.maxRetentionPoint + ")를 초과할 수 없습니다.");
        }
        this.totalPoint += amount;
    }

    /**
     * 포인트 사용
     * @param amount 사용될 금액
     */
    public void usePoint(Long amount) {
        if (this.totalPoint < amount) {
            throw new org.musinsa.payments.point.exception.BusinessException(
                org.musinsa.payments.point.common.ResultCode.POINT_SHORTAGE, "보유 포인트가 부족합니다.");
        }
        this.totalPoint -= amount;
    }

    /**
     * 보유 한도 변경
     * @param maxRetentionPoint 새로운 보유 한도
     */
    public void updateMaxRetentionPoint(Long maxRetentionPoint) {
        this.maxRetentionPoint = maxRetentionPoint;
    }
}
