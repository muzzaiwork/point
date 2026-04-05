package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 포인트 사용 취소 이력 엔티티.
 *
 * <p>하나의 주문({@link Order})에 대해 부분 취소가 여러 번 발생할 수 있으므로,
 * 취소 건별로 별도 레코드를 관리한다.
 *
 * <p>취소 시 복구된 {@link PointEvent}들은 이 엔티티를 참조하여
 * 어떤 취소 요청에 의해 복구되었는지 추적할 수 있다.
 */
@Entity
@Table(name = "order_cancel", indexes = {
        @Index(name = "idx_order_cancel_order_id", columnList = "order_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderCancel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 취소 대상 주문 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /** 이번 취소 요청의 취소 금액 */
    @Column(nullable = false)
    private Long cancelAmount;
}
