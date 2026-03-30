package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 포인트 적립 내역 엔티티
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointAccumulation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, unique = true)
    private String pointKey; // 고유 키

    @Column(nullable = false)
    private Long amount; // 최초 적립 금액

    @Column(nullable = false)
    private Long remainingAmount; // 사용 가능한 잔액

    @Column(nullable = false)
    private boolean isManual; // 수기 지급 여부

    @Column(nullable = false)
    private LocalDateTime accumulationDate;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    private boolean isCancelled; // 적립 취소 여부

    /**
     * 포인트를 사용한다 (잔액 차감)
     * @param useAmount 사용 금액
     */
    public void use(Long useAmount) {
        if (this.remainingAmount < useAmount) {
            throw new IllegalArgumentException("사용 가능한 잔액이 부족합니다.");
        }
        this.remainingAmount -= useAmount;
    }

    /**
     * 포인트를 복구한다 (잔액 증가)
     * @param restoreAmount 복구 금액
     */
    public void restore(Long restoreAmount) {
        this.remainingAmount += restoreAmount;
    }

    /**
     * 적립을 취소한다.
     * 이미 사용된 금액이 있는 경우 취소할 수 없다.
     */
    public void cancel() {
        if (!this.amount.equals(this.remainingAmount)) {
            throw new IllegalStateException("이미 사용된 금액이 있는 경우 적립을 취소할 수 없습니다.");
        }
        this.isCancelled = true;
        this.remainingAmount = 0L;
    }

    /**
     * 테스트를 위해 만료일을 과거로 설정한다.
     */
    public void expireForTest() {
        this.expiryDate = LocalDateTime.now().minusDays(1);
    }

    /**
     * 현재 시점 기준으로 만료 여부를 확인한다.
     * @param now 기준 시간
     * @return 만료 여부
     */
    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiryDate);
    }
}
