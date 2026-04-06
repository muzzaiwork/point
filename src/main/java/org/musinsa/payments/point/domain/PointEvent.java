package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 포인트 이벤트 엔티티.
 *
 * <p>적립·사용·취소·만료·재발급 등 포인트와 관련된 모든 활동을 1원 단위로 기록한다.
 * 이 테이블을 통해 특정 적립 건의 전체 생애주기를 추적할 수 있다.
 *
 * <p>이벤트 타입별 연관 관계:
 * <ul>
 *   <li>{@code ACCUMULATE}: point 연결, order/orderCancel null</li>
 *   <li>{@code ACCUMULATE_CANCEL}: point 연결, order/orderCancel null</li>
 *   <li>{@code USE}: point + order 연결, orderCancel null</li>
 *   <li>{@code USE_CANCEL}: point + order + orderCancel 연결</li>
 *   <li>{@code EXPIRED_CANCEL_RESTORE}: point(신규) + orderCancel 연결, order null</li>
 * </ul>
 */
@Entity
@Table(name = "point_detail", indexes = {
        @Index(name = "idx_pd_order_id",              columnList = "order_id"),
        @Index(name = "idx_pd_point_id", columnList = "point_id"),
        @Index(name = "idx_pd_order_cancel_id",       columnList = "order_cancel_id"),
        @Index(name = "idx_pd_reg_date_time",         columnList = "regDateTime"),
        @Index(name = "idx_pd_reg_date",              columnList = "regDate")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 연관 주문 (USE, USE_CANCEL 이벤트에서 설정) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /** 연관 적립 건 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_id")
    private Point point;

    /** 이벤트 타입 (ACCUMULATE, USE, USE_CANCEL 등) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointEventType pointEventType;

    /** 이벤트 금액 */
    @Column(nullable = false)
    private Long amount;

    /** 연관 취소 이력 (USE_CANCEL, EXPIRED_CANCEL_RESTORE 이벤트에서 설정) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_cancel_id")
    private OrderCancel orderCancel;

    /**
     * 취소 이력을 사후에 연결한다.
     * OrderCancel 저장 후 기존 이벤트에 연결할 때 사용한다.
     *
     * @param orderCancel 연결할 취소 이력
     */
    public void setOrderCancel(OrderCancel orderCancel) {
        this.orderCancel = orderCancel;
    }
}
