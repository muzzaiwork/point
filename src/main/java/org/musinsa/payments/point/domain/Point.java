package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;
import org.musinsa.payments.point.common.ResultCode;
import org.musinsa.payments.point.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 포인트 적립 내역 엔티티.
 *
 * <p>포인트 1건의 생애주기(적립 → 사용 → 취소/만료)를 추적한다.
 * 모든 상태 변경은 이 엔티티의 메서드를 통해서만 이루어진다.
 *
 * <p>주요 필드:
 * <ul>
 *   <li>{@code accumulatedPoint}: 최초 적립 금액 (불변)</li>
 *   <li>{@code remainingPoint}: 현재 사용 가능한 잔액</li>
 *   <li>{@code rootPointKey}: 전체 이력 추적을 위한 최상위 적립 건의 키</li>
 *   <li>{@code originPointKey}: 만료 후 재적립 시 원본 적립 건의 키</li>
 * </ul>
 */
@Entity
@Table(name = "point", indexes = {
        @Index(name = "idx_point_user_id",       columnList = "userId"),
        @Index(name = "idx_point_expiry_date",   columnList = "expiryDateTime"),
        @Index(name = "idx_point_reg_date_time", columnList = "regDateTime"),
        @Index(name = "idx_point_reg_date",      columnList = "regDate"),
        @Index(name = "idx_point_order_no",      columnList = "orderNo")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Point extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 ID */
    @Column(nullable = false)
    private String userId;

    /** 적립 건 고유 키 (yyyyMMdd + 6자리 시퀀스, 예: 20260401000001) */
    @Column(nullable = false, unique = true)
    private String pointKey;

    /** 적립 근거 주문 번호 (선택) */
    private String orderNo;

    /** 최초 적립 금액 (불변) */
    @Column(nullable = false)
    private Long accumulatedPoint;

    /** 현재 사용 가능한 잔액 */
    @Column(nullable = false)
    private Long remainingPoint;

    /** 포인트 타입 (FREE: 무료, PAID: 유료) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType type;

    /** 만료 일시 (시분초 포함) */
    @Column(nullable = false)
    private LocalDateTime expiryDateTime;

    /** 만료 날짜 (날짜 기반 인덱스/조회용) */
    @Column(nullable = false)
    private LocalDate expiryDate;

    /** 적립 취소 여부 */
    private boolean isCancelled;

    /** 만료 처리 여부 */
    private boolean isExpired;

    /** 만료 시점의 잔여 포인트 (만료된 금액 기록용) */
    private Long expiredPoint;

    /** 누적 사용 금액 */
    private Long usedPoint;

    /** 누적 사용 취소(복구) 금액 */
    private Long usedCancelPoint;

    /**
     * 만료 후 취소로 인한 신규 적립 시, 원본 적립 건의 pointKey.
     * 일반 적립 건은 null.
     */
    private String originPointKey;

    /**
     * 전체 이력 추적을 위한 최상위 적립 건의 pointKey.
     * 최초 적립 건은 자기 자신의 pointKey와 동일하며,
     * 만료 후 재적립 시 원본의 rootPointKey를 상속한다.
     */
    private String rootPointKey;

    /** 포인트 적립 원천 타입 (ACCUMULATION, MANUAL, AUTO_RESTORED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointSourceType pointSourceType;

    // =========================================================================
    // 생명주기 콜백
    // =========================================================================

    /**
     * 최초 저장 시 rootPointKey가 null이면 자기 자신의 pointKey로 초기화한다.
     * 일반적으로 {@code doAccumulate()} 내부에서 직접 설정되므로,
     * 이 콜백은 null인 경우에만 동작하는 fallback이다.
     */
    @PostPersist
    public void initRootPointKey() {
        if (this.rootPointKey == null) {
            this.rootPointKey = this.pointKey;
        }
    }

    // =========================================================================
    // 상태 변경 메서드
    // =========================================================================

    /**
     * 포인트를 사용한다 (잔액 차감).
     *
     * @param useAmount 사용 금액
     */
    public void use(Long useAmount) {
        this.remainingPoint -= useAmount;
        this.usedPoint = (this.usedPoint == null ? 0L : this.usedPoint) + useAmount;
    }

    /**
     * 포인트를 복구한다 (잔액 증가).
     * 사용 취소 시 호출된다.
     *
     * @param restoreAmount 복구 금액
     */
    public void restore(Long restoreAmount) {
        this.remainingPoint += restoreAmount;
        this.usedCancelPoint = (this.usedCancelPoint == null ? 0L : this.usedCancelPoint) + restoreAmount;
    }

    /**
     * 적립을 취소한다.
     *
     * <p>취소 불가 조건:
     * <ul>
     *   <li>{@code isCancelled == true}: 이미 취소된 건 → {@code ALREADY_CANCELLED} 예외</li>
     *   <li>{@code remainingPoint != accumulatedPoint}: 일부라도 사용된 건 → {@code ALREADY_USED} 예외</li>
     * </ul>
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
     * 만료일을 변경한다.
     *
     * @param expiryDate 새로운 만료 일시
     */
    public void setExpiryDateTime(LocalDateTime expiryDate) {
        this.expiryDateTime = expiryDate;
        this.expiryDate = expiryDate.toLocalDate();
    }

    /**
     * 포인트를 만료 처리한다.
     * 만료 시점의 잔여 포인트를 기록하고 잔액을 0으로 설정한다.
     */
    public void expire() {
        this.expiredPoint = this.remainingPoint;
        this.isExpired = true;
        this.remainingPoint = 0L;
    }

    /**
     * 특정 시점 기준으로 만료 여부를 확인한다.
     * {@code isExpired} 플래그가 true이거나, 기준 시간이 만료 일시를 지난 경우 만료로 판단한다.
     *
     * @param now 기준 시간
     * @return 만료 여부
     */
    public boolean isExpired(LocalDateTime now) {
        return this.isExpired || now.isAfter(expiryDateTime);
    }
}
