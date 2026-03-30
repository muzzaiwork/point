package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 포인트 사용 상세 내역 엔티티 (어떤 적립 건에서 얼마를 사용했는지 1원 단위로 기록)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointUsageDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_usage_id")
    private PointUsage pointUsage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_accumulation_id")
    private Point point;

    @Column(nullable = false)
    private Long amount; // 해당 적립 건에서 사용한 금액

    @Column(nullable = false)
    private LocalDateTime usageDate;
}
