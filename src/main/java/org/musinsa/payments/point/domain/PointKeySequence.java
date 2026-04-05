package org.musinsa.payments.point.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 날짜별 포인트 키 시퀀스 관리 엔티티.
 *
 * <p>포인트 키는 {@code yyyyMMdd + 6자리 시퀀스} 형식으로 생성된다 (예: {@code 20260401000001}).
 * 날짜가 바뀌면 새 레코드가 생성되고 시퀀스는 1부터 다시 시작한다.
 *
 * <p>동시성 제어:
 * 시퀀스 조회 시 Pessimistic Lock을 적용하여 동일 날짜에 중복 키가 생성되지 않도록 보장한다.
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

    /** 시퀀스 기준 날짜 (unique) */
    @Column(nullable = false, unique = true)
    private LocalDate sequenceDate;

    /** 해당 날짜의 마지막 시퀀스 번호 */
    @Column(nullable = false)
    private Long lastSequence;

    /**
     * 시퀀스를 1 증가시킨다.
     */
    public void increment() {
        this.lastSequence += 1;
    }
}
