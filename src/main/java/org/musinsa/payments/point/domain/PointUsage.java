package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 포인트 사용 내역 엔티티
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String orderNo; // 주문 번호

    @Column(nullable = false, unique = true)
    private String pointKey; // 사용 고유 키

    @Column(nullable = false)
    private Long totalAmount; // 총 사용 금액

    @Column(nullable = false)
    private LocalDateTime usageDate;
}
