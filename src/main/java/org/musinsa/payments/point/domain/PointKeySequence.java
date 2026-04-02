package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 포인트 키 생성을 위한 시퀀스 관리 엔티티
 */
@Entity
@Table(name = "point_key_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointKeySequence extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate sequenceDate; // 시퀀스 기준 날짜

    @Column(nullable = false)
    private Long lastSequence; // 해당 날짜의 마지막 시퀀스 번호

    public void increment() {
        this.lastSequence += 1;
    }
}
