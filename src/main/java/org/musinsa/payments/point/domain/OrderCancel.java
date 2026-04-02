package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 취소 이력 엔티티 (부분 취소가 여러 번 발생할 수 있으므로 별도 관리)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private Long cancelAmount;

    @Column(nullable = false)
    private LocalDateTime cancelledAt;

    @Column(nullable = false)
    private LocalDate cancelledDay;
}
